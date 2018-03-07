//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package edu.iu.dsc.tws.comms.mpi.io.reduce;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.ReduceFunction;
import edu.iu.dsc.tws.comms.api.ReduceReceiver;
import edu.iu.dsc.tws.comms.mpi.io.PartitionData;

public class ReduceStreamingFinalReceiver extends ReduceStreamingReceiver {
  private static final Logger LOG = Logger.getLogger(
      ReduceStreamingPartialReceiver.class.getName());

  private ReduceReceiver reduceReceiver;

  public ReduceStreamingFinalReceiver(ReduceFunction function, ReduceReceiver receiver) {
    super(function);
    this.reduceReceiver = receiver;
  }

  @Override
  public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    super.init(cfg, op, expectedIds);
    this.reduceReceiver.init(cfg, op, expectedIds);
  }

  @Override
  public boolean handleMessage(int source, Object message, int flags, int dest) {
    PartitionData data = (PartitionData) message;
    LOG.info(String.format("%d Emitting final data: src %d target %d id %d",
        executor, source, dest, data.getId()));
    return reduceReceiver.receive(source, message);
  }
}
