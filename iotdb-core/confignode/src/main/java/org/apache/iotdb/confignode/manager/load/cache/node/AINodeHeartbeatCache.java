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

package org.apache.iotdb.confignode.manager.load.cache.node;

import org.apache.iotdb.common.rpc.thrift.TLoadSample;
import org.apache.iotdb.commons.cluster.NodeStatus;
import org.apache.iotdb.confignode.manager.load.cache.AbstractHeartbeatSample;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class AINodeHeartbeatCache extends BaseNodeCache {

  private final AtomicReference<TLoadSample> latestLoadSample;

  public AINodeHeartbeatCache(int aiNodeId) {
    super(aiNodeId);
    this.latestLoadSample = new AtomicReference<>(new TLoadSample());
  }

  @Override
  public void updateCurrentStatistics(boolean forceUpdate) {
    NodeHeartbeatSample lastSample;
    final List<AbstractHeartbeatSample> heartbeatHistory;
    /* Update Node status */
    NodeStatus status = null;
    String statusReason = null;
    long currentNanoTime = System.nanoTime();
    synchronized (slidingWindow) {
      lastSample = (NodeHeartbeatSample) getLastSample();
      heartbeatHistory = Collections.unmodifiableList(slidingWindow);
      /* Update load sample */
      if (lastSample != null && lastSample.isSetLoadSample()) {
        latestLoadSample.set((lastSample.getLoadSample()));
      }

      if (lastSample != null && NodeStatus.Removing.equals(lastSample.getStatus())) {
        status = NodeStatus.Removing;
      } else if (!failureDetector.isAvailable(nodeId, heartbeatHistory)) {
        /* Failure detector decides that this AINode is UNKNOWN */
        status = NodeStatus.Unknown;
      } else if (lastSample != null) {
        status = lastSample.getStatus();
        statusReason = lastSample.getStatusReason();
      }
    }

    long loadScore = NodeStatus.isNormalStatus(status) ? 0 : Long.MAX_VALUE;

    NodeStatistics newStatistics =
        new NodeStatistics(currentNanoTime, status, statusReason, loadScore);
    if (!currentStatistics.get().equals(newStatistics)) {
      // Update the current NodeStatistics if necessary
      currentStatistics.set(newStatistics);
    }
  }
}
