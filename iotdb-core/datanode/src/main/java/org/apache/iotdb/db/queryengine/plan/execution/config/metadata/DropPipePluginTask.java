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

package org.apache.iotdb.db.queryengine.plan.execution.config.metadata;

import org.apache.iotdb.db.queryengine.plan.execution.config.ConfigTaskResult;
import org.apache.iotdb.db.queryengine.plan.execution.config.IConfigTask;
import org.apache.iotdb.db.queryengine.plan.execution.config.executor.IConfigTaskExecutor;
import org.apache.iotdb.db.queryengine.plan.relational.sql.ast.DropPipePlugin;
import org.apache.iotdb.db.queryengine.plan.statement.metadata.pipe.DropPipePluginStatement;

import com.google.common.util.concurrent.ListenableFuture;

public class DropPipePluginTask implements IConfigTask {

  private final DropPipePluginStatement dropPipePluginStatement;

  public DropPipePluginTask(final DropPipePluginStatement dropPipePluginStatement) {
    this.dropPipePluginStatement = dropPipePluginStatement;
  }

  public DropPipePluginTask(final DropPipePlugin node) {
    this.dropPipePluginStatement = new DropPipePluginStatement();
    this.dropPipePluginStatement.setPluginName(node.getPluginName());
    this.dropPipePluginStatement.setIfExists(node.hasIfExistsCondition());
    this.dropPipePluginStatement.setTableModel(true);
  }

  @Override
  public ListenableFuture<ConfigTaskResult> execute(final IConfigTaskExecutor configTaskExecutor)
      throws InterruptedException {
    return configTaskExecutor.dropPipePlugin(dropPipePluginStatement);
  }
}
