/**
 * GenJ - GenealogyJ
 *
 * Copyright (C) 1997 - 2006 Nils Meier <nils@meiers.net>
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

import genj.crypto.Enigma;
import genj.gedcom.Context;
import genj.gedcom.Entity;
import genj.gedcom.Gedcom;
import genj.gedcom.GedcomException;
import genj.gedcom.Property;
import genj.gedcom.PropertyXRef;
import genj.gedcom.Submitter;
import genj.util.Origin;
import genj.util.Resources;
import genj.util.Trackable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GedcomReader is a custom reader for Gedcom compatible information. Normally
 * it's used by GenJ's application or applet when trying to open a file or
 * simply reading from a stream. This type can be used by 3rd parties that
 * are interested in reading Gedcom into the GenJ object representation as well.
 */
public class GedcomReader implements Trackable {

  private final static Resources RESOURCES = Resources.get("genj.io");
  
  private static Logger LOG = Logger.getLogger("genj.io");

  /** estimated average byte size of one entity */
  private final static int ENTITY_AVG_SIZE = 150;
  
  /** stati the reader goes through */
  private final static int READHEADER = 0, READENTITIES = 1, LINKING = 2;

  /** lots of state we keep during reading */
  private Gedcom              gedcom;
  
  private int progress;
  private int entity = 0;
  private int state;
  private int length;
  private String gedcomLine;
  private ArrayList lazyLinks = new ArrayList();
  private String tempSubmitter;
  private boolean cancel=false;
  private Thread worker;
  private Object lock = new Object();
  private EntityReader reader;
  private MeteredInputStream meter;
  
  /** encryption */
  private Enigma enigma;

  /** collecting warnings */
  private List warnings = new ArrayList(128);
  
  /**
   * Constructor for a reader that reads from stream
   */
  public GedcomReader(InputStream in) throws IOException {
    init(new Gedcom(), in);
  }
  
  /**
   * Constructor for a reader that reads from given origin
   * @param origin source of gedcom stream
   */
  public GedcomReader(Origin origin) throws IOException {
    LOG.info("Initializing reader for "+origin);
    init(new Gedcom(origin), origin.open());
  }
  
  /**
   * initializer
   */
  private void init(Gedcom ged, InputStream in) throws IOException {
    SniffedInputStream sniffer = new SniffedInputStream(in);
    init(ged, sniffer, sniffer.getCharset(), sniffer.getEncoding());
  }
  
  private void init(Gedcom ged, InputStream in, Charset charset, String encoding) throws IOException {
    
    // init some data
    length = in.available();
    
    gedcom = ged;
    gedcom.setEncoding(encoding);
    
    meter = new MeteredInputStream(in);
    reader = new EntityReader(new InputStreamReader(meter, charset));

    // Done
  }
  
  /**
   * Set password to use to decrypt private properties
   * @param password password to use or Gedcom.PASSWORD_UNKNOWN - if the password
   *  doesn't match a GedcomEncryptionException is thrown during read
   */
  public void setPassword(String password) {
    
    // valid argument?
    if (password==null)
      throw new IllegalArgumentException("Password can't be NULL");
      
    // set it on Gedcom
    gedcom.setPassword(password); 
    
    // done
  }

  /**
   * Thread-safe cancel of read()
   */
  public void cancelTrackable() {

    // Stop it as soon as possible
    cancel=true;
    synchronized (lock) {
      if (worker!=null)
        worker.interrupt();
    }
    // Done
  }

  /**
   * Returns progress of save in %
   * @return percent as 0 to 100
   */
  public int getProgress() {
    // reading right now?
    if (state==READENTITIES&&length>0) 
        progress = (int)Math.min(100, meter.getCount()*100/length);
      
    // done
    return progress;
  }

  /**
   * Returns current read state as explanatory string
   */
  public String getState() {
    switch (state) {
      case READHEADER :
        return RESOURCES.getString("progress.read.header");
      case READENTITIES :default:
        return RESOURCES.getString("progress.read.entities", new String[]{ ""+reader.getLines(), ""+entity} );
      case LINKING      :
        return RESOURCES.getString("progress.read.linking");
    }
  }

  /**
   * Returns warnings List<Warning> encountered while reading
   * @return the warnings as a list of String
   */
  public List getWarnings() {
    return warnings;
  }
  
  /**
   * number of lines read
   */
  public int getLines() {
    return reader.getLines();
  }

  /**
   * Actually writes the gedcom-information 
   * @exception GedcomIOException reading failed
   * @exception GedcomFormatException reading Gedcom-data brought up wrong format
   * @exception GedcomEncryptionException encountered encrypted property and password didn't match
   */
  public Gedcom read() throws GedcomIOException, GedcomFormatException {
    
    // Remember working thread
    synchronized (lock) {
      worker=Thread.currentThread();
    }
    
    // try it
    try {
      readGedcom();
      return gedcom;
    } catch (GedcomIOException gex) {
      throw gex;
    } catch (Throwable t) {
      // catch anything bubbling up here
      LOG.log(Level.SEVERE, "unexpected throwable", t);
      throw new GedcomIOException(t.toString(), reader.getLines());
    } finally  {
      // close in
      try { reader.in.close(); } catch (Throwable t) {};
      // forget working thread
      synchronized (lock) {
        worker=null;
      }
    }

    // nothing happening here
  }
  
  /**
   * Read Gedcom as a whole
   *
   */
  private void readGedcom() throws IOException {

    long start = System.currentTimeMillis();

    // Read the Header
    readHeader();
    state++;
    long header =System.currentTimeMillis(); 

    // Read records after the other
    while (true) {
      Entity record = reader.readEntity();
      if (record.getTag().equals("TRLR")) {
        gedcom.deleteEntity(record);
        break;
      }
    }
    long records = System.currentTimeMillis();

    // Next state
    state++;

    // Prepare submitter
    if (tempSubmitter.length()>0) {
      try {
        Submitter sub = (Submitter)gedcom.getEntity(Gedcom.SUBM, tempSubmitter.replace('@',' ').trim());
        gedcom.setSubmitter(sub);
      } catch (IllegalArgumentException t) {
        warnings.add(new Warning(0, RESOURCES.getString("read.warn.setsubmitter", tempSubmitter), gedcom));
      }
    }

    // Link references
    linkReferences();
    long linking = System.currentTimeMillis();
    
    // sort warnings
    Collections.sort(warnings);
    
    long total = System.currentTimeMillis();
    LOG.log(Level.FINE, gedcom.getName()+" loaded in "+(total-start)/1000+"s (header "+(header-start)/1000+"s, records "+(records-header)/1000+"s, linking "+(linking-records)/1000+"s)");

    // Done
  }

  /**
   * linkage
   */
  private void linkReferences() {

    // loop over kept references
    for (int i=0,n=lazyLinks.size(); i<n; i++) {
      LazyLink lazyLink = (LazyLink)lazyLinks.get(i);
      try {
        if (lazyLink.xref.getTarget()==null)
          lazyLink.xref.link();
        progress = Math.min(100,(int)(i*(100*2)/n));  // 100*2 because Links are probably backref'd
      } catch (GedcomException ex) {
        warnings.add(new Warning(lazyLink.line, ex.getMessage(), lazyLink.xref));
      }
    }

    // done
  }
  
  /**
   * Read Header
   * @exception GedcomIOException reading from <code>BufferedReader</code> failed
   * @exception GedcomFormatException reading Gedcom-data brought up wrong format
   */
  private boolean readHeader() throws IOException {

    Entity header = reader.readEntity();
    if (!header.getTag().equals("HEAD"))
      throw new GedcomFormatException(RESOURCES.getString("read.error.noheader"),0);
    
    //  0 HEAD
    //  1 SOUR GENJ
    //  2 VERS Version.getInstance().toString()
    //  2 NAME GenealogyJ
    //  2 CORP Nils Meier
    //  3 ADDR http://genj.sourceforge.net
    //  1 DEST ANY
    //  1 DATE date
    //  2 TIME time
    //  1 SUBM '@'+gedcom.getSubmitter().getId()+'@'
    //  1 SUBN '@'+gedcom.getSubmission().getId()+'@'
    //  1 GEDC
    //  2 VERS 5.5
    //  2 FORM Lineage-Linked
    //  1 CHAR encoding
    //  1 LANG language
    //  1 PLAC 
    //  2 FORM place format
    //  1 FILE file

    // check 1 SUBM
    tempSubmitter = header.getPropertyValue("SUBM");
    if (tempSubmitter.length()==0)
      warnings.add(new Warning(0, RESOURCES.getString("read.warn.nosubmitter"), gedcom));

    // check 1 SOUR
    String source = header.getPropertyValue("SOUR");
    if (source.length()==0)
      warnings.add(new Warning(0, RESOURCES.getString("read.warn.nosourceid"), gedcom));

    // check for 
    // 1 GEDC 
    // 2 VERSion and 
    // 2 FORMat
    Property gedc = header.getProperty("GEDC");
    if (gedc==null||gedc.getProperty("VERS")==null||gedc.getProperty("FORM")==null)
      warnings.add(new Warning(0, RESOURCES.getString("read.warn.badgedc"), gedcom));
        
    // check 1 LANG
    String lang = header.getPropertyValue("LANG");
    if (lang.length()>0) {
      gedcom.setLanguage(lang);
      LOG.info("Found LANG "+lang+" - Locale is "+gedcom.getLocale());
    }
      
    // check 1 CHAR
    if (header.getPropertyValue("CHAR").equals("ASCII"))
      warnings.add(new Warning(0, RESOURCES.getString("read.warn.ascii"), gedcom));
      
    // check 
    // 1 PLAC
    // 2 FORM
    Property plac = header.getProperty("PLAC");
    if (plac!=null) {
      String form = plac.getPropertyValue("FORM");
      gedcom.setPlaceFormat(form);
      LOG.info("Found Place.Format "+form);
    }
    
    // get rid of it for now
    gedcom.deleteEntity(header);

    // Done
    return true;
  }

  /**
   * SniffedInputStream
   */
  private static class SniffedInputStream extends BufferedInputStream {
    
    private final byte[]
      BOM_UTF8    = { (byte)0xEF, (byte)0xBB, (byte)0xBF },
      BOM_UTF16BE = { (byte)0xFE, (byte)0xFF },
      BOM_UTF16LE = { (byte)0xFF, (byte)0xFE };
      
    private String encoding;
    private Charset charset;
    
    /**
     * Constructor
     */
    private SniffedInputStream(InputStream in) throws IOException {
      
      super(in, 4096);

      // fill buffer and reset
      super.mark(4096); 
      super.read();
      super.reset();
      
      // BOM present?
      if (matchPrefix(BOM_UTF8)) {
        LOG.info("Found BOM_UTF8 - trying encoding UTF-8");
        charset = Charset.forName("UTF-8");
        encoding = Gedcom.UNICODE;
        return;
      }
      if (matchPrefix(BOM_UTF16BE)) {
        LOG.info("Found BOM_UTF16BE - trying encoding UTF-16BE");
        charset = Charset.forName("UTF-16BE");
        encoding = Gedcom.UNICODE;
        return;
      }
      if (matchPrefix(BOM_UTF16LE)) {
        LOG.info("Found BOM_UTF16LE - trying encoding UTF-16LE");
        charset = Charset.forName("UTF-16LE");
        encoding = Gedcom.UNICODE;
        return;
      }
      
      // sniff gedcom header
      String header = new String(super.buf, super.pos, super.count);
      
      // tests
      if (matchHeader(header,Gedcom.UNICODE)) {
        LOG.info("Found "+Gedcom.UNICODE+" - trying encoding UTF-8");
        charset = Charset.forName("UTF-8");
        encoding = Gedcom.UNICODE;
        return;
      } 
      if (matchHeader(header,Gedcom.ASCII)) {
        // ASCII - 20050705 using Latin1 (ISO-8859-1) from now on to preserve extended ASCII characters
        LOG.info("Found "+Gedcom.ASCII+" - trying encoding ISO-8859-1");
        charset = Charset.forName("ISO-8859-1"); // was ASCII
        encoding = Gedcom.ASCII; 
        return;
      } 
      if (matchHeader(header,Gedcom.ANSEL)) {
        LOG.info("Found "+Gedcom.ANSEL+" - trying encoding ANSEL");
        charset = new AnselCharset();
        encoding = Gedcom.ANSEL;
        return;
      } 
      if (matchHeader(header,Gedcom.ANSI)) {
        LOG.info("Found "+Gedcom.ANSI+" - trying encoding Windows-1252");
        charset = Charset.forName("Windows-1252");
        encoding = Gedcom.ANSI;
        return;
      } 
      if (matchHeader(header,Gedcom.LATIN1)||matchHeader(header,"IBMPC")) { // legacy - old style ISO-8859-1/latin1
        LOG.info("Found "+Gedcom.LATIN1+" or IBMPC - trying encoding ISO-8859-1");
        charset = Charset.forName("ISO-8859-1");
        encoding = Gedcom.LATIN1;
        return;
      } 

      // no clue - will default to Ansel
      LOG.info("Could not sniff encoding - trying ANSEL");
      charset = new AnselCharset();
      encoding = Gedcom.ANSEL;
    }
    
    /**
     * Match a header encoding
     */
    private boolean matchHeader(String header, String encoding) {
      return header.indexOf("1 CHAR "+encoding)>0;
    }
    
    /**
     * Match a prefix byte sequence
     */
    private boolean matchPrefix(byte[] prefix) throws IOException {
      // too match to match?
      if (super.count<prefix.length)
        return false;
      // try it
      for (int i=0;i<prefix.length;i++) {
        if (super.buf[pos+i]!=prefix[i])
          return false;
      }
      // skip match
      super.skip(prefix.length);
      // matched!
      return true;
    }
          
    /**
     * result - charset
     */
    /*result*/ Charset getCharset() {
      return charset;
    }
    
    /**
     * result - encoding
     */
    /*result*/ String getEncoding() {
      return encoding;
    }
    
  } //InputStreamSniffer
  
  /** 
   * our entity reader
   */
  private class EntityReader extends PropertyReader {
    
    private boolean warnedAboutPassword = false;
    
    /** constructor */
    EntityReader(Reader in) {
      super(in, null, false);
    }

    /** read one entity */
    Entity readEntity() throws IOException {
      
      if (!readLine(true)) 
        throw new GedcomFormatException(RESOURCES.getString("read.error.norecord"),lines);

      if (level!=0) 
        throw new GedcomFormatException(RESOURCES.getString("read.error.nonumber"), lines);
      
      // Create entity and read its properties
      Entity result;
      try {
        
        result = gedcom.createEntity(tag, xref);
        
        // warn about missing xref if it's a well known type
        if (result.getClass()!=Entity.class&&xref.length()==0) 
          warnings.add(new Warning(getLines(), RESOURCES.getString("read.warn.recordnoid", Gedcom.getName(tag)), result));

        // preserve value for those who care
        result.setValue(value);
        
        // continue into properties
        readProperties(result, 0, 0);
        
      } catch (GedcomException ex) {
        throw new GedcomIOException(ex.getMessage(), lines);
      }

      // Done
      entity++;
      return result;
    }
    
    /** override read to get a chance to decrypt values */
    protected void readProperties(Property prop, int currentLevel, int pos) throws IOException {
      // let super do its thing
      super.readProperties(prop, currentLevel, pos);
      // decrypt lazy
      decryptLazy(prop);
    }
    
    /**
     * Decrypt a value if necessary
     */
    private void decryptLazy(Property prop) throws GedcomEncryptionException {

      String value = prop.getValue();
      
      // no need to do anything if not encrypted value 
      if (!Enigma.isEncrypted(value))
        return;
        
      // set property private
      prop.setPrivate(true, false);
        
      // no need to do anything for unknown password
      String password = gedcom.getPassword();
      if (password==Gedcom.PASSWORD_UNKNOWN) {
        if (!warnedAboutPassword) {
          warnedAboutPassword = true;
          warnings.add(new Warning(getLines(), RESOURCES.getString("crypt.password.unknown"), prop));
        }
        return;
      }
        
      // not set password with encrypted value is error
      if (password==Gedcom.PASSWORD_NOT_SET) 
        throw new GedcomEncryptionException(RESOURCES.getString("crypt.password.required"), lines);
      
      // try to init decryption
      if (enigma==null) {
        enigma = Enigma.getInstance(password);
        if (enigma==null) {
          if (!warnedAboutPassword) {
            warnedAboutPassword = true;
            warnings.add(new Warning(getLines(), RESOURCES.getString("crypt.password.mismatch"), prop));
          }
          gedcom.setPassword(Gedcom.PASSWORD_UNKNOWN);
          return;
        }
      }

      // try to decrypt    
      try {
        // set decrypted value
        prop.setValue(enigma.decrypt(value));
      } catch (IOException e) {
        throw new GedcomEncryptionException(RESOURCES.getString("crypt.password.invalid"), lines);
      }
        
      // done
    }
    
    /** keep track of xrefs - we're going to link them lazily afterwards */
    protected void link(PropertyXRef xref, int line) {
      // keep as warning
      lazyLinks.add(new LazyLink(xref, line));
    }
    
    /** keep track of empty lines */
    protected void trackEmptyLine() {
      warnings.add(new Warning(getLines(), RESOURCES.getString("read.error.emptyline"), gedcom));
    }
    
    /** keep track of bad levels */
    protected void trackBadLevel(int level, Property parent) {
      warnings.add(new Warning(getLines(), RESOURCES.getString("read.warn.badlevel", ""+level), parent));
    }
    
  } //EntityReader
  
  /**
   * A lazy link
   */
  private static class LazyLink {
    
    private PropertyXRef xref;
    private int line;
    
    LazyLink(PropertyXRef xref, int line) {
      this.xref = xref;
      this.line = line;
    }
  }
  
  /**
   * A generated warning 
   */
  private static class Warning extends Context implements Comparable {
    
    private int lineNumber;
    
    /** constructor */
    private Warning(int lineNumber, Property property) {
      super(property);
      this.lineNumber = lineNumber;
    }
    
    /** constructor */
    private Warning(int lineNumber, String text, Gedcom gedcom) {
      super(gedcom);
      super.setText(RESOURCES.getString("read.warn", new Object[] { Integer.toString(lineNumber), text }));
      this.lineNumber = lineNumber;
    }

    /** constructor */
    private Warning(int lineNumber, String text, Property property) {
      super(property);
      super.setText(RESOURCES.getString("read.warn", new Object[] { Integer.toString(lineNumber), text }));
      this.lineNumber = lineNumber;
    }

    /**
     * compare by line #
     */
    public int compareTo(Object o) {
      Warning that = (Warning)o;
      return lineNumber - that.lineNumber;
    }
    
    /**
     * @see Annotation#setText(String)
     */
    public Context setText(String text) {
      super.setText(RESOURCES.getString("read.warn", new Object[] { Integer.toString(lineNumber), text }));
      return this;
    }
    
  } //Warning
  
  /**
   * A metered input stream
   */
  private static class MeteredInputStream extends InputStream {
    
    private long meter = 0;
    private long marked = -1;
    private InputStream in;
    
    MeteredInputStream(InputStream in) {
      this.in = in;
    }
    
    public long getCount() {
      return meter;
    }

    public int available() throws IOException {
      return in.available();
    }

    public void close() throws IOException {
      in.close();
    }

    public synchronized void mark(int readlimit) {
      in.mark(readlimit);
      marked = meter;
    }

    public boolean markSupported() {
      return in.markSupported();
    }

    public int read() throws IOException {
      meter++;
      return in.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
      int read = in.read(b, off, len);
      meter+=read;
      return read;
    }

    public int read(byte[] b) throws IOException {
      int read = in.read(b);
      meter+=read;
      return read;
    }

    public synchronized void reset() throws IOException {
      if (marked<0)
        throw new IOException("reset() without mark()");
      in.reset();
      meter = marked;
    }

    public long skip(long n) throws IOException {
      int skipped = (int)super.skip(n);
      meter+=skipped;
      return skipped;
    }
    
  } //MeteredInputStream
  
} //GedcomReader