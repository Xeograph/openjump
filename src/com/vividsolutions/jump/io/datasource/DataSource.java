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
package com.vividsolutions.jump.io.datasource;

import com.vividsolutions.jump.coordsys.CoordinateSystem;
import com.vividsolutions.jump.coordsys.CoordinateSystemRegistry;
import com.vividsolutions.jump.feature.FeatureCollection;

import java.util.HashMap;
import java.util.Map;

/**
 * A file, database, web service, or other source of data. To be savable to a
 * project file, a DataSource must not be an anonymous class (because the class
 * name is recorded in the project file) and it must have a parameterless
 * constructor (so it can be reconstructed by simply being instantiated and
 * having #setProperties called).
 */
public abstract class DataSource {

  private Map<String,Object> properties;

  /**
   * Sets properties required to open a DataSource, such as username, password,
   * filename, coordinate system, etc. Called by DataSourceQueryChoosers.
   * @param properties the map containing properties for this DataSource
   */
  public void setProperties(Map<String,Object> properties) {
    this.properties = new HashMap<>(properties);
  }

  public Map<String,Object> getProperties() {
    // This method needs to be public because it is called by Java2XML [Jon
    // Aquino 11/13/2003]

    // I was returning a Collections.unmodifiableMap before, but
    // Java2XML couldn't open it after saving it (can't instantiate
    // java.util.Collections$UnmodifiableMap). [Jon Aquino]
    return properties;
  }

  /**
   * Creates a new Connection to this DataSource.
   * @return a Connection to connect to this DataSource
   */
  public abstract Connection getConnection();

  /**
   * Filename property, used for file-based DataSources
   */
  @Deprecated
  public static final String FILE_KEY = "File";

  /**
   * Uri property, a more generic datasource source description
   */
  public static final String URI_KEY = "Uri";

  /**
   * A property used when the datasource is wrapped into a compressed file
   */
  public static final String COMPRESSED_KEY = "CompressedFile";

  /**
   * A property used to define the datasource encoding
   */
  public static final String CHARSET_KEY = "charset";

  /**
   * A property used to define the Spatial Referebce System registry (ex. EPSG)
   * May replace COORDINATE_SYSTEM_KEY and be used in conjunction with
   * COORDINATE_SYSTEM_CODE and the new cts library
   */
  public static final String COORDINATE_SYSTEM_REGISTRY = "SrsRegistry";

  /**
   * A property used to define the Spatial Reference System code (ex. 4326)
   * May replace COORDINATE_SYSTEM_KEY and be used in conjonction with
   * COORDINATE_SYSTEM_REGISTRY and the new cts library
   */
  public static final String COORDINATE_SYSTEM_CODE = "SrsCode";

  /**
   * Coordinate-system property, used for files and other DataSources that have
   * a single CoordinateSystem
   */
  public static final String COORDINATE_SYSTEM_KEY = "Coordinate System";

  public boolean isReadable() {
    return true;
  }

  public boolean isWritable() {
    return true;
  }

  public FeatureCollection installCoordinateSystem(
      FeatureCollection queryResult, CoordinateSystemRegistry registry) {
    if (queryResult == null) {
      return null;
    }
    String coordinateSystemName;
    try {
      coordinateSystemName = (String) getProperties()
          .get(COORDINATE_SYSTEM_KEY);
    } catch (NullPointerException e) {
      return queryResult;
    }
    if (coordinateSystemName == null) {
      return queryResult;
    }
    CoordinateSystem coordinateSystem = registry.get(coordinateSystemName);
    if (coordinateSystem == null) {
      return queryResult;
    }
    queryResult.getFeatureSchema().setCoordinateSystem(coordinateSystem);
    return queryResult;
  }

}
