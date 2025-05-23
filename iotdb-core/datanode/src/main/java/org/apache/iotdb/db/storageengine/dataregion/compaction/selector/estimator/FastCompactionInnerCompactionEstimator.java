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

package org.apache.iotdb.db.storageengine.dataregion.compaction.selector.estimator;

import org.apache.iotdb.db.storageengine.dataregion.compaction.schedule.CompactionScheduleContext;
import org.apache.iotdb.db.storageengine.dataregion.tsfile.TsFileResource;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.List;

public class FastCompactionInnerCompactionEstimator extends AbstractInnerSpaceEstimator {

  @Override
  public long calculatingMetadataMemoryCost(CompactionTaskInfo taskInfo) {
    long cost = 0;
    // add ChunkMetadata size of MultiTsFileDeviceIterator
    long maxAlignedSeriesMemCost =
        taskInfo.getFileInfoList().stream()
            .mapToLong(fileInfo -> fileInfo.maxMemToReadAlignedSeries)
            .sum();
    long maxNonAlignedSeriesMemCost =
        taskInfo.getFileInfoList().stream()
            .mapToLong(
                fileInfo ->
                    fileInfo.maxMemToReadNonAlignedSeries * config.getSubCompactionTaskNum())
            .sum();
    cost +=
        Math.min(
            Math.max(maxAlignedSeriesMemCost, maxNonAlignedSeriesMemCost),
            taskInfo.getFileInfoList().size()
                * taskInfo.getMaxChunkMetadataNumInDevice()
                * taskInfo.getMaxChunkMetadataSize());

    // add ChunkMetadata size of targetFileWriter
    cost += fixedMemoryBudget;

    return cost;
  }

  @Override
  public long calculatingDataMemoryCost(CompactionTaskInfo taskInfo) throws IOException {
    if (taskInfo.getTotalChunkNum() == 0) {
      return taskInfo.getModificationFileSize();
    }
    int batchSize = config.getCompactionMaxAlignedSeriesNumInOneBatch();
    long maxConcurrentSeriesNum =
        Math.max(
            config.getSubCompactionTaskNum(),
            Math.min(
                batchSize <= 0 ? Integer.MAX_VALUE : batchSize,
                taskInfo.getMaxConcurrentSeriesNum()));
    long averageChunkSize = taskInfo.getTotalFileSize() / taskInfo.getTotalChunkNum();

    long maxConcurrentSeriesSizeOfTotalFiles =
        averageChunkSize
                * taskInfo.getFileInfoList().size()
                * maxConcurrentSeriesNum
                * taskInfo.getMaxChunkMetadataNumInSeries()
            + maxConcurrentSeriesNum * tsFileConfig.getPageSizeInByte();
    long maxTargetChunkWriterSize = config.getTargetChunkSize() * maxConcurrentSeriesNum;
    long targetChunkWriterSize =
        Math.min(maxConcurrentSeriesSizeOfTotalFiles, maxTargetChunkWriterSize);

    long maxConcurrentChunkSizeFromSourceFile =
        (averageChunkSize + tsFileConfig.getPageSizeInByte())
            * maxConcurrentSeriesNum
            * calculatingMaxOverlapFileNumInSubCompactionTask(null, taskInfo.getResources());

    return targetChunkWriterSize
        + maxConcurrentChunkSizeFromSourceFile
        + taskInfo.getModificationFileSize();
  }

  @Override
  public long roughEstimateInnerCompactionMemory(
      @Nullable CompactionScheduleContext context, List<TsFileResource> resources)
      throws IOException {
    if (config.getCompactionMaxAlignedSeriesNumInOneBatch() <= 0) {
      return -1L;
    }
    CompactionTaskMetadataInfo metadataInfo =
        CompactionEstimateUtils.collectMetadataInfoFromCachedFileInfo(
            resources, roughInfoMap, true);
    int maxConcurrentSeriesNum = metadataInfo.getMaxConcurrentSeriesNum(true);
    long maxChunkSize = config.getTargetChunkSize();
    long maxPageSize = tsFileConfig.getPageSizeInByte();
    int maxOverlapFileNum = calculatingMaxOverlapFileNumInSubCompactionTask(context, resources);
    // source files (chunk + uncompressed page) * overlap file num
    // target file (chunk + unsealed page writer)
    return (maxOverlapFileNum + 1) * maxConcurrentSeriesNum * (maxChunkSize + maxPageSize)
        + fixedMemoryBudget
        + metadataInfo.metadataMemCost;
  }

  @Override
  protected int calculatingMaxOverlapFileNumInSubCompactionTask(
      @Nullable CompactionScheduleContext context, List<TsFileResource> resources)
      throws IOException {
    if (resources.get(0).isSeq()) {
      return 1;
    }
    return super.calculatingMaxOverlapFileNumInSubCompactionTask(context, resources);
  }
}
