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
package edu.iu.dsc.tws.comms.mpi;

import java.util.List;

import edu.iu.dsc.tws.comms.api.MessageHeader;
import edu.iu.dsc.tws.comms.routing.IRouter;

public class MPIDataFlowKeyedReduce extends MPIGroupedDataFlowOperation {
  public MPIDataFlowKeyedReduce(TWSMPIChannel channel) {
    super(channel);
  }

  @Override
  protected IRouter setupRouting() {
    return null;
  }

  @Override
  protected void routeReceivedMessage(MessageHeader message, List<Integer> routes) {
    throw new RuntimeException("We cannot route the received message");
  }

  @Override
  protected void routeSendMessage(int source, MPISendMessage message, List<Integer> routes) {

  }
}