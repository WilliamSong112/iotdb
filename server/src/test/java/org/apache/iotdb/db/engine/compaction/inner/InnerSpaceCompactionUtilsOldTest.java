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

package org.apache.iotdb.db.engine.compaction.inner;

import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.constant.TestConstant;
import org.apache.iotdb.db.engine.compaction.inner.utils.InnerSpaceCompactionUtils;
import org.apache.iotdb.db.engine.compaction.inner.utils.SizeTieredCompactionLogger;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.tsfile.read.TsFileReader;
import org.apache.iotdb.tsfile.read.TsFileSequenceReader;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.common.RowRecord;
import org.apache.iotdb.tsfile.read.expression.QueryExpression;
import org.apache.iotdb.tsfile.read.query.dataset.QueryDataSet;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import static org.apache.iotdb.db.engine.compaction.inner.utils.SizeTieredCompactionLogger.SOURCE_INFO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * This test is used for old version of inner space compact. We leave it as it still validates the
 * current compaction. However, due to this test's strong coupling with an older version of
 * compaction, we may remove it in the future.
 */
public class InnerSpaceCompactionUtilsOldTest extends InnerCompactionTest {

  File tempSGDir;

  @Override
  @Before
  public void setUp() throws Exception {
    tempSGDir = new File(TestConstant.getTestTsFileDir("root.compactionTest", 0, 0));
    if (!tempSGDir.exists()) {
      assertTrue(tempSGDir.mkdirs());
    }
    super.setUp();
  }

  @Override
  @After
  public void tearDown() throws IOException, StorageEngineException {
    super.tearDown();
    FileUtils.deleteDirectory(new File("target/testTsFile"));
  }

  @Test
  public void testCompact() throws IOException, MetadataException {
    TsFileResource targetTsFileResource =
        new TsFileResource(
            new File(
                TestConstant.getTestTsFileDir("root.compactionTest", 0, 0)
                    .concat(
                        0
                            + IoTDBConstant.FILE_NAME_SEPARATOR
                            + 0
                            + IoTDBConstant.FILE_NAME_SEPARATOR
                            + 1
                            + IoTDBConstant.FILE_NAME_SEPARATOR
                            + 0
                            + IoTDBConstant.COMPACTION_TMP_FILE_SUFFIX)));
    File targetFile =
        new File(
            TestConstant.getTestTsFileDir("root.compactionTest", 0, 0)
                .concat(
                    0
                        + IoTDBConstant.FILE_NAME_SEPARATOR
                        + 0
                        + IoTDBConstant.FILE_NAME_SEPARATOR
                        + 1
                        + IoTDBConstant.FILE_NAME_SEPARATOR
                        + 0
                        + ".tsfile"));
    if (targetFile.exists()) {
      assertTrue(targetFile.delete());
    }
    SizeTieredCompactionLogger sizeTieredCompactionLogger =
        new SizeTieredCompactionLogger(
            targetTsFileResource.getTsFilePath().concat(".compaction.log"));
    for (TsFileResource resource : seqResources) {
      sizeTieredCompactionLogger.logFileInfo(SOURCE_INFO, resource.getTsFile());
    }
    sizeTieredCompactionLogger.logSequence(true);
    InnerSpaceCompactionUtils.compact(targetTsFileResource, seqResources, COMPACTION_TEST_SG, true);
    InnerSpaceCompactionUtils.moveTargetFile(targetTsFileResource, COMPACTION_TEST_SG);
    sizeTieredCompactionLogger.close();
    Path path = new Path(deviceIds[0], measurementSchemas[0].getMeasurementId());
    try (TsFileSequenceReader reader =
            new TsFileSequenceReader(targetTsFileResource.getTsFilePath());
        TsFileReader readTsFile = new TsFileReader(reader)) {
      QueryExpression queryExpression =
          QueryExpression.create(Collections.singletonList(path), null);
      QueryDataSet queryDataSet = readTsFile.query(queryExpression);
      int cut = 0;
      RowRecord record;
      while (queryDataSet.hasNext()) {
        record = queryDataSet.next();
        assertEquals(record.getTimestamp(), record.getFields().get(0).getDoubleV(), 0.001);
        cut++;
      }
      assertEquals(500, cut);
    }
  }
}
