//
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
package edu.iu.dsc.tws.comms.core;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import edu.iu.dsc.tws.common.config.Config;
import edu.iu.dsc.tws.common.util.ReflectionUtils;
import edu.iu.dsc.tws.comms.api.DataFlowOperation;
import edu.iu.dsc.tws.comms.api.MessageBuilder;
import edu.iu.dsc.tws.comms.api.MessageFormatter;
import edu.iu.dsc.tws.comms.api.MessageReceiver;
import edu.iu.dsc.tws.comms.api.Operation;

public final class DataFlowChannel {
  private static final Logger LOG = Logger.getLogger(DataFlowChannel.class.getName());
  /**
   * The underlying communication implementation
   */
  private Communication communication;

  /**
   * The configuration read from the configuration file
   */
  private Config config;

  /**
   * Instance plan containing mappings from communication specific ids to higher level task ids
   */
  private InstancePlan instancePlan;

  public DataFlowChannel(InstancePlan instancePlan, Config config) {
    this.instancePlan = instancePlan;
    this.config = config;

    // lets load the communication implementation
    String communicationClass = CommunicationContext.getCommunicationClass(config);
    try {
      communication = ReflectionUtils.newInstance(communicationClass);
    } catch (IllegalAccessException | InstantiationException | ClassNotFoundException e) {
      LOG.severe("Failed to load the communications class");
      throw new RuntimeException(e);
    }
  }

  public DataFlowOperation setUpDataFlowOperation(List<Integer> sources, List<Integer> destinations,
                                                  Map<String, Object> configuration,
                                                  Operation operation, int stream,
                                                  MessageReceiver receiver,
                                                  MessageFormatter formatter,
                                                  MessageBuilder builder) {
    // merge with the user specified configuration, user specified will take precedence
    Config mergedCfg = Config.newBuilder().putAll(config).putAll(configuration).build();

    DataFlowOperation dataFlowOperation = null;
    if (operation == Operation.BROADCAST) {
      dataFlowOperation = communication.broadcast();
    } else {
      throw new IllegalArgumentException("Un-recognizable operation: " + operation);
    }

    // intialize the operation
    dataFlowOperation.init(mergedCfg, instancePlan, sources,
        destinations, stream, receiver, formatter, builder);

    return dataFlowOperation;
  }
}