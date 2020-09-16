/*
 * The Unified Mapping Platform (JUMP) is an extensible, interactive GUI 
 * for visualizing and manipulating spatial features with geometry and attributes.
 *
 * JUMP is Copyright (C) 2003 Vivid Solutions
 *
 * This program implements extensions to JUMP and is
 * Copyright (C) 2004 Integrated Systems Analysts, Inc.
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
 * Integrated Systems Analysts, Inc.
 * 630C Anchors St., Suite 101
 * Fort Walton Beach, Florida
 * USA
 *
 * (850)862-7321
 */

package org.openjump.core.ui.plugin.raster;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.Raster;
import java.io.IOException;

import javax.swing.Icon;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;

import org.openjump.core.CheckOS;
import org.openjump.core.rasterimage.RasterImageLayer;
import org.openjump.core.rasterimage.RasterImageLayer.RasterDataNotFoundException;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jump.I18N;
import com.vividsolutions.jump.workbench.WorkbenchContext;
import com.vividsolutions.jump.workbench.model.FenceLayerFinder;
import com.vividsolutions.jump.workbench.plugin.EnableCheckFactory;
import com.vividsolutions.jump.workbench.plugin.MultiEnableCheck;
import com.vividsolutions.jump.workbench.ui.JTablePanel;
import com.vividsolutions.jump.workbench.ui.cursortool.RectangleTool;
import com.vividsolutions.jump.workbench.ui.images.IconLoader;

public class RasterQueryDragTool extends RectangleTool {

   
    protected Coordinate tentativeCoordinate;
    public static final String LAYER_NAME = I18N
            .get("org.openjump.core.ui.plugin.raster.RasterImageLayerPropertiesPlugIn.cell.values");
    public static final String LAYER = I18N
            .get("org.openjump.core.ui.plugin.raster.RasterImageLayerPropertiesPlugIn.layer");
 
    RasterImageLayer rLayer;

    public RasterQueryDragTool() {
    	setColor(Color.red);
   	 	setStroke(new BasicStroke(1.5F));

    }

    @Override
    public Icon getIcon() {
        return IconLoader.icon("information_16x16.png");
    }

    @Override
    public Cursor getCursor() {
        // [ede 03.2103] linux currently support only 2 color cursors
        Image i = !CheckOS.isLinux() ? IconLoader
                .image("information_cursor.png") : IconLoader
                .image("information_cursor_2color.gif");
        return createCursor(i);
    }

    @Override
    protected void gestureFinished() throws NoninvertibleTransformException,
            IOException, RasterDataNotFoundException {
        reportNothingToUndoYet();

       

        final WorkbenchContext wbcontext = this.getWorkbench().getContext();
        @SuppressWarnings("unchecked")
		RasterImageLayer[] ls = (RasterImageLayer[]) wbcontext.getLayerableNamePanel()
                .selectedNodes(RasterImageLayer.class)
                .toArray(new RasterImageLayer[] {});
                
        if (ls != null && ls.length > 0) {
        	rLayer = ls[0];
        	
        	reportNothingToUndoYet();

            //Don't want viewport to change at this stage. [Jon Aquino]
            getPanel().setViewportInitialized(true);

            FenceLayerFinder fenceLayerFinder = new FenceLayerFinder(getPanel());
            fenceLayerFinder.setFence(getRectangle());

            if (!fenceLayerFinder.getLayer().isVisible()) {
                fenceLayerFinder.getLayer().setVisible(true);
            }
        	
            printArray(rLayer, getRectangle());
        }      

    }
 
  

    public MultiEnableCheck createEnableCheck(
            final WorkbenchContext workbenchContext) {
        EnableCheckFactory checkFactory = new EnableCheckFactory(
                workbenchContext);
        /*
         * Works only with one selected RasterImageLayer
         */
        return new MultiEnableCheck()
                .add(checkFactory
                        .createWindowWithLayerNamePanelMustBeActiveCheck())
                .add(checkFactory
                        .createWindowWithLayerViewPanelMustBeActiveCheck())
                .add(checkFactory.createExactlyNLayerablesMustBeSelectedCheck(
                        1, RasterImageLayer.class));

    }

      
   
    
    @Override
    public String getName() {
    	return  I18N.get("org.openjump.core.ui.plugin.raster.RasterQueryPlugIn");
    }
    
    
    
    public  void printArray(RasterImageLayer rLayer, Geometry fence) throws NoninvertibleTransformException, IOException {
    	 Rectangle subset;
         Raster raster = null;
         Envelope envWanted;
    	 
		envWanted = fence.getEnvelopeInternal().intersection(
	   rLayer.getWholeImageEnvelope());
		subset = rLayer.getRectangleFromEnvelope(envWanted);
		raster = rLayer.getRasterData(subset);
		  final int w = raster.getWidth(), h = raster.getHeight();
		 
		  Object[][] data = new Object[w][h];
		  Object columnNames[] = new Object[w];
        for (int x = 0; x < w; x++) {
        	columnNames[x]="Col"+x;
            for (int y = 0; y < h; y++) {
                data[x][y] = raster.getSampleDouble(x, y, 0);
            }
        }
        
        DefaultTableModel tableModel = new DefaultTableModel(data, columnNames);
      
        final JTablePanel jTablePanel = new JTablePanel(tableModel);
        jTablePanel.getCommandPanel().removeAll();
        jTablePanel.getTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        jTablePanel.getTable().setTableHeader(null);
        
       
        jTablePanel.addHierarchyListener(new HierarchyListener() {
    		@Override
			public void hierarchyChanged(HierarchyEvent e) {
    			Window window = SwingUtilities.getWindowAncestor(jTablePanel);
    			if (window instanceof Dialog) {
    				Dialog dialog = (Dialog) window;
    				if (!dialog.isResizable()) {
    					dialog.setResizable(true);
    				}
    			}
    		}
    	});
        
        
        jTablePanel.setPreferredSize(new Dimension(400,300));
        
        
        JOptionPane.showOptionDialog(null, jTablePanel, I18N.get("org.openjump.core.ui.plugin.raster.RasterQueryPlugIn"), JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE, null, 
      		  new Object[]{}, null);
     }
}