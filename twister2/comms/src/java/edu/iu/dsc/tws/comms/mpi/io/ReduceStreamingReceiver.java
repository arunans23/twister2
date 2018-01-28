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
package edu.iu.dsc.tws.comms.mpi.io;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageReceiver;
import edu.iu.dsc.tws.comms.api.ReduceFunction;
import edu.iu.dsc.tws.comms.mpi.MPIContext;

public abstract class ReduceStreamingReceiver implements MessageReceiver {
  private static final Logger LOG = Logger.getLogger(ReduceStreamingReceiver.class.getName());

  protected ReduceFunction reduceFunction;
  // lets keep track of the messages
  // for each task we need to keep track of incoming messages
  protected Map<Integer, Map<Integer, List<Object>>> messages = new HashMap<>();
  protected Map<Integer, Map<Integer, Integer>> counts = new HashMap<>();
  protected int executor;
  protected int count = 0;
  protected DataFlowOperation operation;
  protected int sendPendingMax = 128;
  protected int destination = 0;

  public ReduceStreamingReceiver(ReduceFunction function) {
    this.reduceFunction = function;
  }

  @Override
  public void init(Config cfg, DataFlowOperation op, Map<Integer, List<Integer>> expectedIds) {
    this.executor = op.getTaskPlan().getThisExecutor();
    this.operation = op;
    sendPendingMax = MPIContext.sendPendingMax(cfg);

    for (Map.Entry<Integer, List<Integer>> e : expectedIds.entrySet()) {
      Map<Integer, List<Object>> messagesPerTask = new HashMap<>();
      Map<Integer, Integer> countsPerTask = new HashMap<>();

      for (int i : e.getValue()) {
        messagesPerTask.put(i, new ArrayList<Object>());
        countsPerTask.put(i, 0);
      }

      LOG.fine(String.format("%d Final Task %d receives from %s",
          executor, e.getKey(), e.getValue().toString()));

      messages.put(e.getKey(), messagesPerTask);
      counts.put(e.getKey(), countsPerTask);
    }
  }

  @Override
  public boolean onMessage(int source, int path, int target, int flags, Object object) {
    // add the object to the map
    boolean canAdd = true;
    List<Object> m = messages.get(target).get(source);
    Integer c = counts.get(target).get(source);
    if (m.size() > sendPendingMax) {
      canAdd = false;
    } else {
      m.add(object);
      counts.get(target).put(source, c + 1);
    }

    return canAdd;
  }

  public void progress() {
    for (int t : messages.keySet()) {
      boolean canProgress = true;
      while (canProgress) {
        // now check weather we have the messages for this source
        Map<Integer, List<Object>> messagePerTarget = messages.get(t);
        Map<Integer, Integer> countsPerTarget = counts.get(t);
        boolean found = true;
        for (Map.Entry<Integer, List<Object>> e : messagePerTarget.entrySet()) {
          if (e.getValue().size() == 0) {
            found = false;
            canProgress = false;
          }
        }
        if (found) {
          Object previous = null;
          for (Map.Entry<Integer, List<Object>> e : messagePerTarget.entrySet()) {
            if (previous == null) {
              previous = e.getValue().get(0);
            } else {
              Object current = e.getValue().get(0);
              previous = reduceFunction.reduce(previous, current);
            }
          }

          boolean handle = handleMessage(t, previous, 0, destination);
          if (handle) {
            for (Map.Entry<Integer, List<Object>> e : messagePerTarget.entrySet()) {
              e.getValue().remove(0);
            }
            for (Map.Entry<Integer, Integer> e : countsPerTarget.entrySet()) {
              Integer i = e.getValue();
              countsPerTarget.put(e.getKey(), i - 1);
            }
          } else {
            canProgress = false;
          }
        }
      }
    }
  }

  public abstract boolean handleMessage(int source, Object message, int flags, int dest);
}