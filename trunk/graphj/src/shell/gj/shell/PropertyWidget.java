/**
 * GraphJ
 * 
 * Copyright (C) 2002 Nils Meier
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package gj.shell;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import gj.shell.swing.GBLayout;
import gj.shell.util.ReflectHelper;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A widget that shows public attribues of an instance as properties 
 * in Swing components
 */ 
public class PropertyWidget extends JPanel {
  
  /** list of Boolean values */
  private final static Boolean[] BOOLEANS = new Boolean[] {
    Boolean.TRUE,Boolean.FALSE
  };
  
  /** the instance */
  private Object instance;
  
  /** its properties */
  private ReflectHelper.Property[] properties;
  
  /** the components */
  private Map components;
  
  /** biggest preferred size */
  private Dimension biggestPreferredSize = new Dimension(0,0);
  
  /** whether we'll ignore action events for a little while */
  private boolean isIgnoreActionEvent = false;
  
  /** an ActionListener we keep for combos */
  private ActionListener alistener = new ActionListener() {
    public void actionPerformed(ActionEvent e) {
      if (isIgnoreActionEvent) return;
      EventListener[] es = listenerList.getListeners(ActionListener.class);
      for (int i=0; i<es.length; i++) {
        ((ActionListener)es[i]).actionPerformed(e);
      }
    }
  };
  
  /** 
   * Constructor
   */
  public PropertyWidget() {
  }
  
  /**
   * Updates the instance with current values
   */
  public void commit() {

    // safety
    if (instance==null) return;
    
    // gather the values
    for (int p=0; p<properties.length; p++) {
      ReflectHelper.Property prop = properties[p];
      prop.value = getValue((JComponent)components.get(prop.name));
    }
    
    // set those values
    ReflectHelper.setProperties(instance, properties);
    
    // done
  }
  
  /**
   * Refreshes from current instance
   */
  public void refresh() {
    
    // safety
    if (instance==null) return;
    
    // get the properties again
    properties = ReflectHelper.getProperties(instance, true);
    
    // gather the values
    for (int p=0; p<properties.length; p++) {
      ReflectHelper.Property prop = properties[p];
      setValue((JComponent)components.get(prop.name), prop.value);
    }
    
    // done
  }
  
  /**
   * Checks whether given instance has properties
   */
  public static boolean hasProperties(Object instance) {
    return ReflectHelper.getProperties(instance, true).length!=0;
  }
  
  /** 
   * Sets the instance we're looking at
   */
  public PropertyWidget setInstance(Object instance) {
    
    // remember the instance and get its properties
    this.instance = instance;
    properties = ReflectHelper.getProperties(instance, true);
  
    // start with a GBLayout
    GBLayout layout = new GBLayout(this);
    
    // nothing to do?
    if (properties.length==0) {
      
      layout.add(new JLabel("No Properties"),0,0,1,1,false,false,true,true);
      
    } else {
  
      // loop through properties
      components = new HashMap(properties.length);

      for (int p=0; p<properties.length; p++) {

        ReflectHelper.Property prop = properties[p];
        
        JComponent component = getComponent(prop.value);
        components.put(prop.name, component);
        
        layout.add(new JLabel(prop.name),0,p,1,1,false,false,true,false);
        layout.add(component            ,1,p,1,1,true ,false,true,false);
       
      }
      
      layout.add(new JLabel(),0,properties.length,2,1,true,true,true,true);

    }
    
    // make sure that is shown
    revalidate();
    repaint();
        
    // done
    return this;
  }

  /**
   * Returns a component appropriate for editing given property
   */
  private JComponent getComponent(Object prop) {
    if (prop instanceof Boolean) {
      JComboBox cb = new JComboBox(BOOLEANS);
      cb.setSelectedItem(prop);
      cb.addActionListener(alistener);
      return cb;
    }
    return new JTextField(prop.toString());
  }  
  
  /**
   * Returns the value of given JComponent 
   */
  private Object getValue(JComponent component) {
    if (component instanceof JComboBox)
      return ((JComboBox)component).getSelectedItem();
    return ((JTextField)component).getText();
  }
  
  /**
   * Returns the value of given JComponent 
   */
  private void setValue(JComponent component, Object value) {
    if (component instanceof JComboBox) {
      isIgnoreActionEvent=true;
      JComboBox cb = (JComboBox)component;
      cb.setSelectedItem(value);
      isIgnoreActionEvent=false;
    }
    else ((JTextField)component).setText(value.toString());
  }
  
  /**
   * @see Component#getPreferredSize()
   */
  public Dimension getPreferredSize() {
    Dimension d = super.getPreferredSize();
    d.width = Math.max(d.width,biggestPreferredSize.width);
    d.height = Math.max(d.height,biggestPreferredSize.height);
    biggestPreferredSize = d;
    return d;
  }
  
  
  /**
   * Adds an ActionListener
   */
  public void addActionListener(ActionListener a) {
    listenerList.add(ActionListener.class,a);
  }
  
  /**
   * Removes an ActionListener
   */
  public void removeActionListener(ActionListener a) {
    listenerList.remove(ActionListener.class,a);
  }

}
