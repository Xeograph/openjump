/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * Copyright (C) 2003 Vivid Solutions
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * For more information, contact:
 *
 * Vivid Solutions
 * Suite #1A
 * 2328 Government Street
 * Victoria BC  V8T 5G5
 * Canada
 *
 * (250)385-6040
 * www.vividsolutions.com
 */
package com.vividsolutions.jump.workbench.model;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.SwingUtilities;

import org.locationtech.jts.util.Assert;
import com.vividsolutions.jump.feature.Feature;
import com.vividsolutions.jump.feature.FeatureCollection;
import com.vividsolutions.jump.feature.FeatureCollectionWrapper;
import com.vividsolutions.jump.feature.FeatureDataset;
import com.vividsolutions.jump.feature.FeatureSchema;
import com.vividsolutions.jump.feature.Operation;
import com.vividsolutions.jump.io.datasource.DataSourceQuery;
import com.vividsolutions.jump.workbench.ui.plugin.AddNewLayerPlugIn;
import com.vividsolutions.jump.workbench.ui.renderer.style.BasicStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.LabelStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.SquareVertexStyle;
import com.vividsolutions.jump.workbench.ui.renderer.style.Style;
import com.vividsolutions.jump.workbench.ui.renderer.style.VertexStyle;
import org.openjump.core.ccordsys.srid.SRIDStyle;

/**
 * Adds colour, line-width, and other stylistic information to a Feature
 * Collection.
 * <p>
 * When adding or removing multiple features to this Layer's FeatureCollection,
 * prefer #addAll and #removeAll to #add and #remove -- fewer events will be
 * fired.
 */
public class Layer extends GeoReferencedLayerable implements Disposable {

  private static final String FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE = Layer.class
      .getName() + " - FIRING APPEARANCE CHANGED ON ATTRIBUTE CHANGE";

  private String description = "";

  private boolean drawingLast = false;

  private FeatureCollectionWrapper featureCollectionWrapper;

  private final List<Style> styles = new ArrayList<>();

  private boolean synchronizingLineColor = true;

  private LayerListener layerListener = null;

  //private Blackboard blackboard = new Blackboard() {
  //  private static final long serialVersionUID = 6504993615735124204L;
  //  {
  //    put(FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE, true);
  //  }
  //};

  private boolean featureCollectionModified = false;

  private DataSourceQuery dataSourceQuery;

  /**
   * Called by Java2XML
   */
  public Layer() {
    getBlackboard().put(FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE, true);
  }

  public Layer(String name, Color fillColor,
      FeatureCollection featureCollection, LayerManager layerManager) {
    super(name, layerManager);
    getBlackboard().put(FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE, true);
    Assert.isTrue(featureCollection != null);
    setLayerManager(layerManager);
    // Can't fire events because this Layerable hasn't been added to the
    // LayerManager yet. [Jon Aquino]
    boolean firingEvents = layerManager.isFiringEvents();
    layerManager.setFiringEvents(false);

    try {
      addStyle(new BasicStyle());
      addStyle(new SquareVertexStyle());
      addStyle(new LabelStyle());
    } finally {
      layerManager.setFiringEvents(firingEvents);
    }

    getBasicStyle().setFillColor(fillColor);
    getBasicStyle().setLineColor(defaultLineColor(fillColor));
    getBasicStyle().setAlpha(150);
    getVertexStyle().setFillColor(fillColor);
    getVertexStyle().setLineColor(defaultLineColor(fillColor));
    getVertexStyle().setAlpha(150);
    getVertexStyle().setEnabled(false);
    setFeatureCollection(featureCollection);
  }

  /**
   * @param fillColor the fillColor we want to derive a lineColor
   * @return a darker version of the given fill colour, for use as the line
   *         colour
   */
  public static Color defaultLineColor(Color fillColor) {
    return fillColor.darker();
  }

  public void setDescription(String description) {
    Assert.isTrue(
            description != null,
            "Java2XML requires that the description be non-null. Use an empty string if necessary.");
    this.description = description;
  }

  /**
   * Used for lightweight layers like the Vector layer.
   *
   * @param drawingLast
   *          true if the layer should be among those drawn last
   */
  public void setDrawingLast(boolean drawingLast) {
    this.drawingLast = drawingLast;
    fireAppearanceChanged();
  }

  public void setFeatureCollection(final FeatureCollection featureCollection) {
    final FeatureCollection oldFeatureCollection = featureCollectionWrapper != null ? featureCollectionWrapper
        .getUltimateWrappee() : AddNewLayerPlugIn
        .createBlankFeatureCollection();
    ObservableFeatureCollection observableFeatureCollection = new ObservableFeatureCollection(
        featureCollection);
    observableFeatureCollection.checkNotWrappingSameClass();
    observableFeatureCollection.add(new ObservableFeatureCollection.Listener() {
      public void featuresAdded(Collection<Feature> features) {
        getLayerManager().fireFeaturesChanged(features, FeatureEventType.ADDED,
            Layer.this);
      }

      public void featuresRemoved(Collection<Feature> features) {
        getLayerManager().fireFeaturesChanged(features,
            FeatureEventType.DELETED, Layer.this);
      }
    });

    if ((getLayerManager() != null)
        && getLayerManager().getLayers().contains(this)
        && getLayerManager().isFiringEvents()) {
      // Don't fire APPEARANCE_CHANGED immediately, to avoid the
      // following problem:
      // (1) Add fence layer
      // (2) LAYER_ADDED event will be called
      // (3) APPEARANCE_CHANGED will be fired in this method (before
      // the JTree receives its LAYER_ADDED event)
      // (4) The JTree will complain because it gets the
      // APPEARANCE_CHANGED
      // event before the LAYER_ADDED event:
      // java.lang.ArrayIndexOutOfBoundsException: 0 >= 0
      // at java.util.Vector.elementAt(Vector.java:412)
      // at
      // javax.swing.tree.DefaultMutableTreeNode.getChildAt(DefaultMutableTreeNode.java:226)
      // at
      // javax.swing.tree.VariableHeightLayoutCache.treeNodesChanged(VariableHeightLayoutCache.java:369)
      // at
      // javax.swing.plaf.basic.BasicTreeUI$TreeModelHandler.treeNodesChanged(BasicTreeUI.java:2339)
      // at
      // javax.swing.tree.DefaultTreeModel.fireTreeNodesChanged(DefaultTreeModel.java:435)
      // at
      // javax.swing.tree.DefaultTreeModel.nodesChanged(DefaultTreeModel.java:318)
      // at
      // javax.swing.tree.DefaultTreeModel.nodeChanged(DefaultTreeModel.java:251)
      // at
      // com.vividsolutions.jump.workbench.model.LayerTreeModel.layerChanged(LayerTreeModel.java:292)
      // [Jon Aquino]
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          // Changed APPEARANCE_CHANGED event to FEATURE_DELETED and
          // FEATURE_ADDED events, but I think the lengthy comment
          // above still applies. [Jon Aquino]
          // Drop #isEmpty checks, so that database-backed feature
          // collections don't have to implement it.
          // [Jon Aquino 2005-03-02]
          getLayerManager().fireFeaturesChanged(
              oldFeatureCollection.getFeatures(), FeatureEventType.DELETED,
              Layer.this);
          getLayerManager().fireFeaturesChanged(
              featureCollection.getFeatures(), FeatureEventType.ADDED,
              Layer.this);
        }
      });
    }

    setFeatureCollectionWrapper(observableFeatureCollection);
  }

  public void setSynchronizingLineColor(boolean synchronizingLineColor) {
    this.synchronizingLineColor = synchronizingLineColor;
    fireAppearanceChanged();
  }

  public BasicStyle getBasicStyle() {
    return (BasicStyle) getStyle(BasicStyle.class);
  }

  public VertexStyle getVertexStyle() {
    return (VertexStyle) getStyle(VertexStyle.class);
  }

  public LabelStyle getLabelStyle() {
    return (LabelStyle) getStyle(LabelStyle.class);
  }

  public String getDescription() {
    return description;
  }

  /**
   * Returns a wrapper around the FeatureCollection which was added using
   * #wrapFeatureCollection. The original FeatureCollection can be retrieved
   * using FeatureCollectionWrapper#getWrappee. However, parties are encouraged
   * to use the FeatureCollectionWrapper instead, so that feature additions and
   * removals cause FeatureEvents to be fired (by the Layer).
   * @return the FeatureCollectionWrapper containing features
   */
  public FeatureCollectionWrapper getFeatureCollectionWrapper() {
    return featureCollectionWrapper;
  }

  protected void setFeatureCollectionWrapper(
      FeatureCollectionWrapper featureCollectionWrapper) {
    this.featureCollectionWrapper = featureCollectionWrapper;
    // To set FeatureSchema's dynamic attributes (AKA Operations), we need
    // a reference to the FeatureSchema. This is the reason why it is not
    // done immediately by the xml2java deserialization but here, after the
    // FeatureCollection has been set.
    setFeatureSchemaOperations();
  }

  /**
   * Styles do not notify the Layer when their parameters change. Therefore,
   * after you modify a Style's parameters (for example, the fill colour of
   * BasicStyle), be sure to call #fireAppearanceChanged
   *
   * @param c
   *          Can even be the desired Style's superclass or interface
   * @return The style value
   */
  public Style getStyle(Class<?> c) {
    for (Style style : getStyles()) {
      if (c.isInstance(style)) {
        return style;
      }
    }
    return null;
  }

  /**
   * get a list of all enabled styles matching the parameter class
   * @param filter superclass of the requested styles
   * @return a list containing enabled styles implementing the requested class
   */
  public List<Style> getStylesIfEnabled(Class<?> filter) {
    List<Style> enabledStyles = new ArrayList<>();
    final List<Style> someStyles = getStyles(filter);
    for (Style style : someStyles) {
      if (style.isEnabled())
        enabledStyles.add(style);
    }
    return Collections.unmodifiableList(enabledStyles);
  }

  /**
   * get a list of all styles
   * @return all Styles of this Layer in a new unmodifiable List
   */
  public List<Style> getStyles() {
    return Collections.unmodifiableList(styles);
  }

  /**
   * get a list of all styles matching the parameter class
   * @param filter superclass or interface of the requested styles
   * @return a list containing all styles implementing the requested class
   */
  public List<Style> getStyles(Class<?> filter) {
    List<Style> someStyles = new ArrayList<>();
    final Collection<Style> allStyles = getStyles();
    for (Style style : allStyles) {
      if (filter.isInstance(style)) {
        someStyles.add(style);
      }
    }
    return Collections.unmodifiableList(someStyles);
  }

  public boolean hasReadableDataSource() {
    return dataSourceQuery != null
        && dataSourceQuery.getDataSource().isReadable();
  }

  public boolean isDrawingLast() {
    return drawingLast;
  }

  public boolean isSynchronizingLineColor() {
    return synchronizingLineColor;
  }

  public void addStyle(Style style) {
    styles.add(style);
    fireAppearanceChanged();
  }

  /**
   * Releases references to the data, to facilitate garbage collection.
   * Important for MDI apps like the JUMP Workbench. Called when the last
   * JInternalFrame viewing the LayerManager is closed (i.e. internal frame's
   * responsibility). To conserve memory, if layers are frequently added and
   * removed from the LayerManager, parties may want to call #dispose themselves
   * rather than waiting for the internal frame to be closed.
   */
  public void dispose() {
    // dispose features if disposable nature
    Collection<Feature> features = getFeatureCollectionWrapper().getFeatures();
    for (Feature feature : features) {
      if (feature instanceof Disposable) {
        ((Disposable) feature).dispose();
      }
    }
    // Don't just call FeatureCollection#removeAll, because it may be a
    // database table, and we don't want to delete its contents! [Jon Aquino]
    setFeatureCollection(AddNewLayerPlugIn.createBlankFeatureCollection());
  }

  public void removeStyle(Style p) {
    Assert.isTrue(styles.remove(p));
    fireAppearanceChanged();
  }

  public Collection<Style> cloneStyles() {
    ArrayList<Style> styleClones = new ArrayList<>();
    for (Style style : getStyles()) {
      styleClones.add((Style) style.clone());
    }
    return styleClones;
  }

  public void setStyles(Collection<Style> newStyles) {
    boolean firingEvents = getLayerManager().isFiringEvents();
    getLayerManager().setFiringEvents(false);

    try {
      // new ArrayList to prevent ConcurrentModificationException [Jon
      // Aquino]
      for (Style style : new ArrayList<>(getStyles())) {
        removeStyle(style);
      }

      for (Style style : newStyles) {
        addStyle(style);
      }
    } finally {
      getLayerManager().setFiringEvents(firingEvents);
    }

    fireAppearanceChanged();
  }

  @Override
  public void setLayerManager(LayerManager layerManager) {
    if (layerManager != null) {
      layerManager.removeLayerListener(getLayerListener());
    }
    super.setLayerManager(layerManager);
    if (layerManager != null) {
      layerManager.addLayerListener(getLayerListener());
    }
  }

  LayerListener getLayerListener() {
    // Need to create layerListener lazily because it will be called by the
    // superclass constructor. [Jon Aquino]
    // Do not call getLayerListener in superclass constructor or layerListener
    // will be erased during the instance initialization [mmichaud]
    if (layerListener == null) {
      layerListener = new LayerListener() {
        public void featuresChanged(FeatureEvent e) {
          if (e.getLayer() == Layer.this) {
            setFeatureCollectionModified(true);

            // Before I wasn't firing appearance-changed on an
            // attribute
            // change. But now with labelling and colour theming,
            // I have to. [Jon Aquino]
            if (e.getType() != FeatureEventType.ATTRIBUTES_MODIFIED
                || getBlackboard().get(
                    FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE, true)) {
              // Fixed bug above -- wasn't supplying a default
              // value to
              // Blackboard#getBoolean, resulting in a
              // NullPointerException
              // when the Layer was created using the
              // parameterless
              // constructor (because that constructor doesn't
              // initialize
              // FIRING_APPEARANCE_CHANGED_ON_ATTRIBUTE_CHANGE
              // on the blackboard [Jon Aquino 10/21/2003]
              fireAppearanceChanged();
            }
            // Taken from SRIDStyle class to set SRID on new features
            SRIDStyle ss = (SRIDStyle) getStyle(SRIDStyle.class);
            if (e.getType() != FeatureEventType.DELETED && ss != null) {
              for (Feature feature : e.getFeatures()) {
                feature.getGeometry().setSRID(ss.getSRID());
              }
            }
          }
        }

        public void layerChanged(LayerEvent e) {
        }

        public void categoryChanged(CategoryEvent e) {
        }
      };
    }
    return layerListener;
  }

  //public Blackboard getBlackboard() {
    //return blackboard;
  //}

  /**
   * Enables a layer to be changed undoably. Since the layer's features are
   * saved, only use this method for layers with few features.
   * @param layerName the target Layer name
   * @param proxy a LayerManager proxy
   * @param wrappeeCommand the undoable command
   * @return the command to be executed
   */
  public static UndoableCommand addUndo(final String layerName,
                                        final LayerManagerProxy proxy,
                                        final UndoableCommand wrappeeCommand) {
    return new UndoableCommand(wrappeeCommand.getName()) {

      private Layer layer;

      private String categoryName;

      private Collection<Feature> features;

      private boolean visible;

      private Layer currentLayer() {
        return proxy.getLayerManager().getLayer(layerName);
      }

      public void execute() {
        layer = currentLayer();

        if (layer != null) {
          features = new ArrayList<>(layer.getFeatureCollectionWrapper()
              .getFeatures());
          categoryName = layer.getName();
          visible = layer.isVisible();
        }

        wrappeeCommand.execute();
      }

      public void unexecute() {
        wrappeeCommand.unexecute();

        if ((layer == null) && (currentLayer() != null)) {
          proxy.getLayerManager().remove(currentLayer());
        }

        if ((layer != null) && (currentLayer() == null)) {
          proxy.getLayerManager().addLayer(categoryName, layer);
        }

        if (layer != null) {
          layer.getFeatureCollectionWrapper().clear();
          layer.getFeatureCollectionWrapper().addAll(features);
          layer.setVisible(visible);
        }
      }
    };
  }

  /**
   * Does nothing if the underlying feature collection is not a FeatureDataset.
   * @param layer the Layer whose envelope must be invalidated
   */
  public static void tryToInvalidateEnvelope(Layer layer) {
    if (layer.getFeatureCollectionWrapper().getUltimateWrappee() instanceof FeatureDataset) {
      ((FeatureDataset) layer.getFeatureCollectionWrapper()
          .getUltimateWrappee()).invalidateEnvelope();
    }
  }

  public DataSourceQuery getDataSourceQuery() {
    return dataSourceQuery;
  }

  public Layer setDataSourceQuery(DataSourceQuery dataSourceQuery) {
    this.dataSourceQuery = dataSourceQuery;

    return this;
  }

  public boolean isFeatureCollectionModified() {
    return featureCollectionModified;
  }

  public Layer setFeatureCollectionModified(boolean featureCollectionModified) {
    if (this.featureCollectionModified == featureCollectionModified) {
      return this;
    }

    this.featureCollectionModified = featureCollectionModified;
    fireLayerChanged(LayerEventType.METADATA_CHANGED);

    return this;
  }

  public Collection<String> getFeatureSchemaOperations() {
    FeatureSchema fs = getFeatureCollectionWrapper().getFeatureSchema();
    List<String> operations = new ArrayList<>();
    for (int i = 0; i < fs.getAttributeCount(); i++) {
      Operation operation = fs.getOperation(i);
      if (operation != null)
        operations.add(fs.getOperation(i).toString());
      else
        operations.add("NULL");
    }
    return Collections.unmodifiableCollection(operations);
  }

  // Used for Operation deserialization
  private Collection<String> expressions;

  // TODO Why not on FeatureCollection or FeatureSchema ?
  public void addFeatureSchemaOperation(String expression) {
    if (expressions == null)
      expressions = new ArrayList<>();
    expressions.add(expression);
  }

  // TODO Why not on FeatureCollection or FeatureSchema ?
  private void setFeatureSchemaOperations() {
    FeatureCollection fc = getFeatureCollectionWrapper();
    if (expressions != null && fc != null
        && expressions.size() == fc.getFeatureSchema().getAttributeCount()) {
      FeatureSchema schema = fc.getFeatureSchema();
      Iterator<String> it = expressions.iterator();
      for (int i = 0; i < schema.getAttributeCount(); i++) {
        try {
          String expression = it.next();
          if (expression != null && !expression.equals("NULL")
              && expression.indexOf('\n') > -1) {
            String[] class_expression = expression.split("\n", 2);
            Operation op = org.openjump.core.feature.AttributeOperationFactory
                .getFactory(class_expression[0]).createOperation(
                    schema.getAttributeType(i), class_expression[1]);
            schema.setOperation(i, op);
            schema.setAttributeReadOnly(i, true);
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    expressions = null;
  }

}