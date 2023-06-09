/**
 * GenJ - GenealogyJ
 *
 * Copyright (C) 1997 - 2002 Nils Meier <nils@meiers.net>
 *
 * This piece of code is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package genj.io;

import genj.Version;
import genj.crypto.Enigma;
import genj.gedcom.Entity;
import genj.gedcom.Gedcom;
import genj.gedcom.Property;
import genj.gedcom.time.PointInTime;
import genj.util.Trackable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnmappableCharacterException;
import java.nio.charset.UnsupportedCharsetException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * GedcomWriter is a custom write for Gedcom compatible information. Normally
 * it's used by GenJ's application when trying to save to a file. This type 
 * can be used by 3rd parties that are interested in writing Gedcom from
 * a GenJ object-representation managed outside of GenJ as well.
 */
public class GedcomWriter implements Trackable {

  private static Logger LOG = Logger.getLogger("genj.io");
  
  /** lots of state */
  private Gedcom gedcom;
  private BufferedWriter out;
  private String file;
  private String date;
  private String time;
  private int total;
  private int line;
  private int entity;
  private boolean cancel = false;
  private Filter filter;
  private Enigma enigma = null;

  /**
   * Constructor for a writer that will write gedcom-formatted output
   * on writeGedcom()
   * @param ged object to write out
   * @param stream the stream to write to
   */
  public GedcomWriter(Gedcom ged, OutputStream stream) throws IOException, GedcomEncodingException  {
    
    Calendar now = Calendar.getInstance();

    // init data
    gedcom = ged;
    file = ged.getOrigin()==null ? "Uknown" : ged.getOrigin().getFileName();
    line = 0;
    date = PointInTime.getNow().getValue();
    time = new SimpleDateFormat("HH:mm:ss").format(now.getTime());
    filter = new Filter.Union(gedcom, Collections.<Filter>emptyList());

    CharsetEncoder encoder = getCharset(false, stream, ged.getEncoding()).newEncoder();
    encoder.onUnmappableCharacter(CodingErrorAction.REPORT);
    out = new BufferedWriter(new OutputStreamWriter(stream, encoder));
    
    // Done
  }

  /**
   * Create the charset we're using for out
   */
  private Charset getCharset(boolean writeBOM, OutputStream out, String encoding) throws GedcomEncodingException {

    // Attempt encoding
    try {
      // Unicode
      if (Gedcom.UNICODE.equals(encoding)) {
        if (writeBOM) try {
          out.write(GedcomEncodingSniffer.BOM_UTF16BE);
        } catch (Throwable t) {
          // ignored
        }
        return Charset.forName("UTF-16BE");
      }
      // UTF8
      if (Gedcom.UTF8.equals(encoding)) {
        if (writeBOM) try {
          out.write(GedcomEncodingSniffer.BOM_UTF8);
        } catch (Throwable t) {
          // ignored
        }
        return Charset.forName("UTF-8");
      }
      // ASCII - 20050705 using Latin1 (ISO-8859-1) from now on to preserve extended ASCII characters
      if (Gedcom.ASCII.equals(encoding))
        return Charset.forName("ISO-8859-1"); // was ASCII
      // Latin1 (ISO-8859-1)
      if (Gedcom.LATIN1.equals(encoding))
        return Charset.forName("ISO-8859-1");
      // ANSI (Windows-1252)
      if (Gedcom.ANSI.equals(encoding))
        return Charset.forName("Windows-1252");
    } catch (UnsupportedCharsetException e) {
    }

    // ANSEL
    if (Gedcom.ANSEL.equals(encoding)) 
      return new AnselCharset();
      
    // unknown encoding
    throw new GedcomEncodingException("Can't write with unknown encoding " + encoding);

  }

  /**
   * Thread-safe cancel of writeGedcom()
   */
  public void cancelTrackable() {
    cancel = true;
  }

  /**
   * Returns progress of save in %
   * @return percent as 0 to 100
   */
  public int getProgress() {
    if (entity == 0) 
      return 0;
    return entity * 100 / total;
  }

  /**
   * Returns current write state as string
   */
  public String getState() {
    return line + " Lines & " + entity + " Entities";
  }

  /**
   * Sets filters to use for checking whether to write 
   * entities/properties or not
   */
  public void setFilters(Collection<Filter> fs) {
    filter = new Filter.Union(gedcom, fs);
  }
  
  /**
   * Number of lines written
   */
  public int getLines() {
    return line;
  }
  
  /**
   * Actually writes the gedcom-information 
   * @exception GedcomIOException
   */
  public void write() throws GedcomIOException {

    // check state - we pass gedcom only once!
    if (gedcom==null)
      throw new IllegalStateException("can't call write() twice");
    
    List<Entity> ents = gedcom.getEntities(); 
    total = ents.size();

    // Out operation
    try {

      // Data
      writeHeader();
      writeEntities(ents);
      writeTail();

      // Close Output
      out.close();

    } catch( GedcomIOException ioe ) {
      throw ioe;
    } catch (Exception ex) {
      throw new GedcomIOException("Error while writing / "+ex.getMessage(), line);
    } finally {
      gedcom = null;
    }

    // Done
  }
  
  /** write line for header and footer */
  private void writeLine(String line) throws IOException {
    out.write(line);
    out.newLine();
    this.line++;
  }
  
  /**
   * Write Header information
   * @exception IOException
   */
  private void writeHeader() throws IOException {
    
    // Header
    writeLine( "0 HEAD");
    writeLine( "1 SOUR GENJ");
    writeLine( "2 VERS "+Version.getInstance());
    writeLine( "2 NAME GenealogyJ");
    writeLine( "2 CORP Nils Meier");
    writeLine( "3 ADDR http://genj.sourceforge.net");
    writeLine( "1 DEST ANY");
    writeLine( "1 DATE "+date);
    writeLine( "2 TIME "+time);
    if (gedcom.getSubmitter()!=null)
      writeLine( "1 SUBM @"+gedcom.getSubmitter().getId()+'@');
    writeLine( "1 FILE "+file);
    writeLine( "1 GEDC");
    writeLine( "2 VERS "+gedcom.getGrammar().getVersion());
    writeLine( "2 FORM Lineage-Linked");
    writeLine( "1 CHAR "+gedcom.getEncoding());
    if (gedcom.getLanguage()!=null)
      writeLine( "1 LANG "+gedcom.getLanguage());
    if (gedcom.getPlaceFormat().length()>0) {
      writeLine( "1 PLAC");
      writeLine( "2 FORM "+gedcom.getPlaceFormat());
    }
    // done
  }

  /**
   * Write Entities information
   * @exception IOException
   */
  private void writeEntities(List<Entity> entities) throws IOException {

    // Loop through entities
    es: for (Entity e : entities) {
      // .. check op
      if (cancel) 
        throw new GedcomIOException("Operation cancelled", line);
      // .. filtered?
      if (filter.veto(e))
        continue es;
      // .. writing it and its subs
      try {
        line += new EntityWriter().write(0, e);
      } catch(UnmappableCharacterException unme) {
        throw new GedcomEncodingException(e, gedcom.getEncoding());
      }

      // .. track it
      entity++;
    }

    // Done
  }

  /**
   * Write Tail information
   * @exception IOException
   */
  private void writeTail() throws IOException {
    // Tailer
    writeLine("0 TRLR");
  }

  /**
   * our entity writer
   */
  private class EntityWriter extends PropertyWriter {
    
    /** constructor */
    EntityWriter() {
      super(out, false);
    }

    /** intercept prop decoding to check filters */
    protected void writeProperty(int level, Property prop) throws IOException {
      
      // check against filters
      if (!prop.isTransient() ) {
        if (filter.veto(prop))
          return;
      }
      // cont
      super.writeProperty(level, prop);
    }
     
    /** intercept value decoding to facilitate encryption */
    protected String getValue(Property prop) throws IOException {
      return prop.isPrivate() ? encrypt(prop.getValue()) : super.getValue(prop);
    }
    
    /**
     * encrypt a value
     */
    private String encrypt(String value) throws IOException {
      
      // not necessary for gedcom without password or empty values
      if (gedcom.getPassword()==null || value.length()==0)
        return value;
      
      // Make sure enigma is setup
      if (enigma==null) {

        // no need if password is unknown (data is already/still encrypted)
        if (gedcom.getPassword()==Gedcom.PASSWORD_UNKNOWN)
          return value;
          
        // error if password isn't set    
        if (gedcom.getPassword()==null)
          throw new IOException("Password not set - needed for encryption");
          
        // error if can't encrypt
        enigma = Enigma.getInstance(gedcom.getPassword());
        if (enigma==null) 
          throw new IOException("Encryption not available");
          
      }
      
      // encrypt and done
      return enigma.encrypt(value);
    }

  } //EntityDecoder
  
} //GedcomWriter
