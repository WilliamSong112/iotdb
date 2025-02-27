/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.protocol.influxdb.dto;

import org.apache.iotdb.db.exception.metadata.IllegalPathException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.path.PartialPath;
import org.apache.iotdb.db.protocol.influxdb.meta.MetaManager;
import org.apache.iotdb.db.qp.physical.crud.InsertRowPlan;
import org.apache.iotdb.db.utils.DataTypeUtils;
import org.apache.iotdb.db.utils.ParameterUtils;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;

import org.influxdb.dto.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class IoTDBPoint {

  private final String deviceId;
  private final long time;
  private final List<String> measurements;
  private final List<TSDataType> types;
  private final List<Object> values;

  public IoTDBPoint(
      String deviceId,
      long time,
      List<String> measurements,
      List<TSDataType> types,
      List<Object> values) {
    this.deviceId = deviceId;
    this.time = time;
    this.measurements = measurements;
    this.types = types;
    this.values = values;
  }

  public IoTDBPoint(String database, Point point, MetaManager metaManager) {
    String measurement = null;
    Map<String, String> tags = new HashMap<>();
    Map<String, Object> fields = new HashMap<>();
    Long time = null;
    TimeUnit precision = TimeUnit.NANOSECONDS;
    // Get the precision of point in influxdb by reflection
    for (java.lang.reflect.Field reflectField : point.getClass().getDeclaredFields()) {
      reflectField.setAccessible(true);
      try {
        if (reflectField.getType().getName().equalsIgnoreCase("java.util.concurrent.TimeUnit")
            && reflectField.getName().equalsIgnoreCase("precision")) {
          precision = (TimeUnit) reflectField.get(point);
        }
      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException(e.getMessage());
      }
    }
    // Get the property of point in influxdb by reflection
    for (java.lang.reflect.Field reflectField : point.getClass().getDeclaredFields()) {
      reflectField.setAccessible(true);
      try {
        if (reflectField.getType().getName().equalsIgnoreCase("java.util.Map")
            && reflectField.getName().equalsIgnoreCase("fields")) {
          fields = (Map<String, Object>) reflectField.get(point);
        }
        if (reflectField.getType().getName().equalsIgnoreCase("java.util.Map")
            && reflectField.getName().equalsIgnoreCase("tags")) {
          tags = (Map<String, String>) reflectField.get(point);
        }
        if (reflectField.getType().getName().equalsIgnoreCase("java.lang.String")
            && reflectField.getName().equalsIgnoreCase("measurement")) {
          measurement = (String) reflectField.get(point);
        }
        if (reflectField.getType().getName().equalsIgnoreCase("java.lang.Number")
            && reflectField.getName().equalsIgnoreCase("time")) {
          time = (Long) reflectField.get(point);
          time = TimeUnit.MILLISECONDS.convert(time, precision);
        }

      } catch (IllegalAccessException e) {
        throw new IllegalArgumentException(e.getMessage());
      }
    }
    // set current time
    if (time == null) {
      time = System.currentTimeMillis();
    }
    ParameterUtils.checkNonEmptyString(database, "database");
    ParameterUtils.checkNonEmptyString(measurement, "measurement name");
    String path = metaManager.generatePath(database, measurement, tags);
    List<String> measurements = new ArrayList<>();
    List<TSDataType> types = new ArrayList<>();
    List<Object> values = new ArrayList<>();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      measurements.add(entry.getKey());
      Object value = entry.getValue();
      types.add(DataTypeUtils.normalTypeToTSDataType(value));
      values.add(value);
    }
    this.deviceId = path;
    this.time = time;
    this.measurements = measurements;
    this.types = types;
    this.values = values;
  }

  public String getDeviceId() {
    return deviceId;
  }

  public long getTime() {
    return time;
  }

  public List<String> getMeasurements() {
    return measurements;
  }

  public List<TSDataType> getTypes() {
    return types;
  }

  public List<Object> getValues() {
    return values;
  }

  public InsertRowPlan convertToInsertRowPlan()
      throws IllegalPathException, IoTDBConnectionException, QueryProcessException {
    return new InsertRowPlan(
        new PartialPath(getDeviceId()),
        getTime(),
        getMeasurements().toArray(new String[0]),
        DataTypeUtils.getValueBuffer(getTypes(), getValues()),
        false);
  }
}
