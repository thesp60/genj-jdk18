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
package genj.table;

import genj.gedcom.Entity;
import genj.gedcom.Gedcom;
import genj.gedcom.GedcomListener;
import genj.gedcom.Property;
import genj.gedcom.PropertyXRef;
import genj.gedcom.TagPath;
import genj.io.Filter;
import genj.renderer.PropertyRenderer;
import genj.util.ActionDelegate;
import genj.util.Registry;
import genj.util.Resources;
import genj.util.swing.ButtonHelper;
import genj.util.swing.HeadlessLabel;
import genj.util.swing.SortableTableHeader;
import genj.view.ContextSupport;
import genj.view.FilterSupport;
import genj.view.ToolBarSupport;
import genj.view.ViewManager;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

/**
 * Component for showing entities of a gedcom file in a tabular way
 */
public class TableView extends JPanel implements ToolBarSupport, ContextSupport, FilterSupport {

  /** a static set of resources */
  private Resources resources = Resources.get(this);
  
  /** the gedcom we're looking at */
  private Gedcom gedcom;
  
  /** the manager around us */
  private ViewManager manager;
  
  /** the registry we keep */
  private Registry registry;
  
  /** the title we keep */
  private String title;
  
  /** the table we're using */
  private JTable table;
  
  /** the table model we're using */
  private EntityTableModel tableModel;
  
  /** the gedcom listener we're using */
  private GedcomListener listener;
  
  /** a callback for selections */
  private SelectionCallback selectionCallback = new SelectionCallback();
  
  /**
   * Constructor
   */
  public TableView(String titl, Gedcom gedcom, Registry registry, ViewManager mgr) {
    
    // keep some stuff
    this.gedcom = gedcom;
    this.registry = registry;
    this.title = titl;
    this.manager = mgr;
    
    // create the underlying model
    tableModel = new EntityTableModel(gedcom);

    // read properties
    loadProperties();
    
    // create our table
    table = new JTable(tableModel) {
      /** whenever new columns need to be created */
      public void createDefaultColumnsFromModel() {
        setColumnModel(((EntityTableModel)getModel()).createTableColumnModel());
      }
    };
    table.setTableHeader(new SortableTableHeader());
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setAutoCreateColumnsFromModel(true);
    table.getTableHeader().setReorderingAllowed(false);
    table.setDefaultRenderer(Object.class, new PropertyTableCellRenderer());
    table.getSelectionModel().addListSelectionListener(selectionCallback);
    
    setLayout(new BorderLayout());
    add(new JScrollPane(table), BorderLayout.CENTER);
    
    // done
  }
  
  /**
   * @see javax.swing.JComponent#getPreferredSize()
   */
  public Dimension getPreferredSize() {
    return new Dimension(480,320);
  }
  
  /**
   * @see java.awt.Component#removeNotify()
   */
  public void removeNotify() {
    // save state
    saveProperties();
    // delegate
    super.removeNotify();
    // destruct model
    tableModel.destructor();
  }
 
  /**
   * Accessor - the paths we're using for given type
   */
  /*package*/ void setPaths(String type, TagPath[] set) {
    tableModel.getMode(type).setPaths(set);
  }

  /**
   * Accessor - the paths we're using for given type
   */
  /*package*/ TagPath[] getPaths(String type) {
    return tableModel.getMode(type).getPaths();
  }

  /**
   * Sets the type of entities to look at
   */
  /*package*/ void setType(String type) {
    grabColumnWidths();
    tableModel.setMode(type);
  }
  
  /**
   * Accessor - the type of entities we're looking at
   */
  /*package*/ String getType() {
    return tableModel.getType();
  }
  
  /**
   * @see genj.view.ContextPopupSupport#setContext(genj.gedcom.Property)
   */
  public void setContext(Property property) {
    // a type that we're interested in?
    // 20030730 Bug #780563 - used == instead of equals 
    Entity entity = property.getEntity();
    if (!entity.getTag().equals(tableModel.getType())) return;
    // already selected?
    int row = table.getSelectionModel().getLeadSelectionIndex();
    if (row>=0 && row<tableModel.getRowCount() && tableModel.getEntity(row)==entity) return;
    // change selection
    row = tableModel.getRow(entity);
    table.scrollRectToVisible(table.getCellRect(row,0,true));
    selectionCallback.skipNext();
    table.getSelectionModel().setSelectionInterval(row,row);
    // done
  }

  /**
   * @see genj.view.ToolBarSupport#populate(JToolBar)
   */
  public void populate(JToolBar bar) {
    // create buttons for mode switch
    ButtonHelper bh = new ButtonHelper();
    bh.setFocusable(false);
    for (int t=0;t<Gedcom.ENTITIES.length;t++) {
      bar.add(bh.create(new ActionChangeType(Gedcom.ENTITIES[t])));
    }
    // done
  }
  
  /**
   * @see genj.view.EntityPopupSupport#getEntityPopupContainer()
   */
  public JComponent getContextPopupContainer() {
    return table;
  }

  /**
   * @see genj.view.EntityPopupSupport#getContextAt(Point)
   */
  public Context getContextAt(Point pos) {
    int row = table.rowAtPoint(pos);
    if (row<0) return null;
    int col = table.columnAtPoint(pos);
    // select it
    table.getSelectionModel().setSelectionInterval(row, row);
    // context is either entity or property
    if (col>=0) {
      Property prop = (Property)tableModel.getValueAt(row, col);
      if (prop!=null) return new Context(prop);
    }
    return new Context(tableModel.getEntity(row));
  }

  /**
   * Grab current column widths
   */
  private void grabColumnWidths() {
    // grab column widths
    TableColumnModel columns = table.getColumnModel();
    int[] widths = new int[columns.getColumnCount()];
    for (int c=0; c<columns.getColumnCount(); c++) {
      widths[c] = columns.getColumn(c).getWidth();
    }
    tableModel.getMode().setWidths(widths);
    // done
  }

  /**
   * Read properties from registry
   */
  private void loadProperties() {

    // get paths&widths
    for (int t=0; t<Gedcom.ENTITIES.length; t++) {
      
      String tag = Gedcom.ENTITIES[t];
      EntityTableModel.Mode mode = tableModel.getMode(tag);
      
      String[] ps = registry.get(tag+".paths" , (String[])null);
      if (ps!=null) mode.setPaths(TagPath.toArray(ps));
      
      int[]    ws = registry.get(tag+".widths", (int[]   )null);
      if (ws!=null) mode.setWidths(ws);

      mode.setSort(registry.get(tag+".sort", new Point(0, 1)));

    }

    // get current filter
    tableModel.setMode(registry.get("mode", Gedcom.INDI));
    
    // Done
  }
  
  /**
   * Write properties from registry
   */
  private void saveProperties() {
    // grab the column widths as they are right now
    grabColumnWidths();
    // save current type
    registry.put("mode",tableModel.getType());
    // save paths&widths
    for (int t=0; t<Gedcom.ENTITIES.length; t++) {
      String tag = Gedcom.ENTITIES[t];
      EntityTableModel.Mode mode = tableModel.getMode(tag);
      
      registry.put(tag+".paths" , mode.getPaths());
      registry.put(tag+".widths", mode.getWidths());
      registry.put(tag+".sort"  , mode.getSort());
    }
    // Done
  }  
  
  
  /**
   * @see genj.view.FilterSupport#getFilter()
   */
  public Filter getFilter() {
    return new SelectionFilter(tableModel, table.getSelectedRows());
  }
  
  /**
   * @see genj.view.FilterSupport#getFilterName()
   */
  public String getFilterName() {
    return table.getSelectedRowCount()+" selected rows in "+title;
  }

  /**
   * SelectionFilter
   */
  private static class SelectionFilter implements Filter {
    /** selected entities */
    private Set ents = new HashSet();
    /** type we're looking at */
    private String type;
    /**
     * Constructor
     */
    private SelectionFilter(EntityTableModel model, int[] rows) {
      type = model.getType();
      for (int r=0; r<rows.length; r++) {
        ents.add(model.getEntity(rows[r]));
      }
    }
    /**
     * has to be in res
     * @see genj.io.Filter#accept(genj.gedcom.Entity)
     */
    public boolean accept(Entity ent) {
      // fam/indi
      if (ent.getTag()==type)
        return ents.contains(ent);
      // maybe a referenced other type?
      Entity[] refs = PropertyXRef.getReferences(ent);
      for (int r=0; r<refs.length; r++) {
        if (ents.contains(refs[r])) return true;
      }
      // not
      return false;
      
    }
    /** @see genj.io.Filter#accept(genj.gedcom.Property) */
    public boolean accept(Property property) {
      return true;
    }
  } //SelectionFilter
  
  /**
   * Action - flip view to entity type
   */
  private class ActionChangeType extends ActionDelegate {
    /** the type this action triggers */
    private String type;
    /** constructor */
    ActionChangeType(String t) {
      type = t;
      setTip(resources.getString("mode.tip", Gedcom.getEntityName(type,true)));
      setImage(Gedcom.getEntityImage(type));
    }
    /** run */
    public void execute() {
      setType(type);
    }
  } //ActionMode

  /**
   * Callback for list selections
   */
  private class SelectionCallback implements ListSelectionListener {
    /** skip next callback */
    private boolean skip = false;
    /**
     * set to skip
     */
    private void skipNext() {
      skip = true;
    }
    /**
     * @see javax.swing.event.ListSelectionListener#valueChanged(javax.swing.event.ListSelectionEvent)
     */
    public void valueChanged(ListSelectionEvent e) {
      // skip this callback?
      if (skip) {
        skip = false;
        return;
      } 
      // grab row & col
      int 
        row = table.getSelectedRow(),
        col = table.getSelectedColumn();
      // find property
      Property context = null;
      // 20040405 make sure we got a row when asking for property row,col
      if (col>=0&&row>=0) context = tableModel.getProperty(row,col);
      if (context==null&&row>=0) context = tableModel.getEntity(row);
      if (context==null) return;
      // set
      manager.setContext(context);
    }
  } //SelectionCallback
  
  /**
   * Renderer for properties in cells
   */
  private class PropertyTableCellRenderer extends HeadlessLabel implements TableCellRenderer {
    /** current property */
    private Property prop;
    /** attributes */
    private boolean isSelected;
    /**
     * @see javax.swing.table.TableCellRenderer#getTableCellRendererComponent(JTable, Object, boolean, boolean, int, int)
     */
    public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focs, int row, int col) {
      // there's a property here
      prop = (Property)value;
      // and some status
      isSelected = selected;
      // ready
      return this;
    }
    /**
     * @see genj.util.swing.HeadlessLabel#paint(java.awt.Graphics)
     */
    public void paint(Graphics g) {
      // our bounds
      Rectangle bounds = getBounds();
      bounds.x=0; bounds.y=0;
      // background?
      if (isSelected) {
        g.setColor(table.getSelectionBackground());
        g.fillRect(0,0,bounds.width,bounds.height);
        g.setColor(table.getSelectionForeground());
      } else {
        g.setColor(table.getForeground());
      }
      g.setFont(table.getFont());
      // no prop and we're done
      if (prop==null) return;
      // get the proxy
      PropertyRenderer proxy = PropertyRenderer.get(prop);
      // let it render
      proxy.render(g, bounds, prop, proxy.PREFER_DEFAULT, manager.getDPI());
      // done
    }
  } //PropertyTableCellRenderer
    
} //TableView
