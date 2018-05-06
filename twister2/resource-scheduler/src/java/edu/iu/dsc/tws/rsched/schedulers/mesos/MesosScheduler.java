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
package edu.iu.dsc.tws.rsched.schedulers.mesos;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.google.protobuf.ByteString;

import org.apache.mesos.Protos;
import org.apache.mesos.Protos.Filters;
import org.apache.mesos.Protos.Offer;
import org.apache.mesos.Protos.TaskInfo;
import org.apache.mesos.Scheduler;
import org.apache.mesos.SchedulerDriver;

import edu.iu.dsc.tws.common.config.Config;



public class MesosScheduler implements Scheduler {
  public static final Logger LOG = Logger.getLogger(MesosScheduler.class.getName());
  private int taskIdCounter = 0;
  private Config config;
  private MesosController controller;
  private int completedTaskCounter = 0;
  private int totalTaskCount;
  private final String jobName;
  private int workerCounter = 0;
  public MesosScheduler(MesosController controller, Config mconfig, String jobName) {
    this.controller = controller;
    this.config = mconfig;
    totalTaskCount = MesosContext.numberOfContainers(config);
    this.jobName = jobName;
  }

  @Override
  public void registered(SchedulerDriver schedulerDriver,
                         Protos.FrameworkID frameworkID, Protos.MasterInfo masterInfo) {
    System.out.println("Registered" + frameworkID);
  }

  @Override
  public void reregistered(SchedulerDriver schedulerDriver,
                           Protos.MasterInfo masterInfo) {
    System.out.println("Re-registered");
  }

  public boolean contains(String[] nodes, Protos.Offer offer) {
    for (String node : nodes) {
      if (offer.getHostname().equals(node)) {
        return true;
      }
    }
    return false;
  }
  @Override
  public void resourceOffers(SchedulerDriver schedulerDriver,
                             List<Protos.Offer> offers) {
    String[] desiredNodes = MesosContext.getDesiredNodes(config).split(",");
    if (taskIdCounter < totalTaskCount) {
      for (Protos.Offer offer : offers) {
        if (!MesosContext.getDesiredNodes(config).equals("all") && !contains(desiredNodes, offer)) {
          continue;
        }
        LOG.info("Offer comes from host ...:" + offer.getHostname());
        if (controller.isResourceSatisfy(offer)) {

          MesosPersistentVolume pv = new MesosPersistentVolume(
              controller.createPersistentJobDirName(jobName), workerCounter);
          pv.getJobDir();
          pv.getWorkerDir();
          Protos.ExecutorInfo executorInfo =
              controller.getExecutorInfo(jobName,
                  MesosPersistentVolume.WORKER_DIR_NAME_PREFIX + workerCounter++);

          Offer.Operation.Launch.Builder launch = Offer.Operation.Launch.newBuilder();
          for (int i = 0; i < MesosContext.containerPerWorker(config); i++) {
            Protos.TaskID taskId = buildNewTaskID();

            int begin = MesosContext.getWorkerPort(config) + taskIdCounter * 10;
            int end = begin + 5;
            TaskInfo task = TaskInfo.newBuilder()
                .setName("task " + taskId).setTaskId(taskId)
                .setSlaveId(offer.getSlaveId())
                .addResources(buildResource("cpus", MesosContext.cpusPerContainer(config)))
                .addResources(buildResource("mem", MesosContext.ramPerContainer(config)))
                .addResources(buildRangeResource("ports", begin, end))
                .setData(ByteString.copyFromUtf8("" + taskId.getValue()))
                .setExecutor(Protos.ExecutorInfo.newBuilder(executorInfo))
                .build();

            launch.addTaskInfos(TaskInfo.newBuilder(task));
          }

          List<Protos.OfferID> offerIds = new ArrayList<>();
          offerIds.add(offer.getId());
          List<Protos.Offer.Operation> operations = new ArrayList<>();
          Offer.Operation operation = Offer.Operation.newBuilder()
              .setType(Offer.Operation.Type.LAUNCH)
              .setLaunch(launch)
              .build();

          operations.add(operation);

          Filters filters = Filters.newBuilder().setRefuseSeconds(1).build();
          schedulerDriver.acceptOffers(offerIds, operations, filters);
          LOG.info("Offer from host" + offer.getHostname() + "has been accepted.");

        }

        if (taskIdCounter >= totalTaskCount - 1) {
          return;
        }
      }
    }
  }


  private Protos.TaskID buildNewTaskID() {
    return Protos.TaskID.newBuilder()
        .setValue(Integer.toString(taskIdCounter++)).build();
  }

  private Protos.Resource buildResource(String name, double value) {
    return Protos.Resource.newBuilder().setName(name)
        .setType(Protos.Value.Type.SCALAR)
        .setScalar(buildScalar(value)).build();
  }

  private Protos.Resource buildRangeResource(String name, int begin, int end) {
    Protos.Value.Range range = Protos.Value.Range.newBuilder().setBegin(begin).setEnd(end).build();
    Protos.Value.Ranges ranges = Protos.Value.Ranges.newBuilder().addRange(range).build();
    return Protos.Resource.newBuilder().setName(name)
        .setType(Protos.Value.Type.RANGES)
        .setRanges(ranges).build();
  }

  private Protos.Value.Scalar.Builder buildScalar(double value) {
    return Protos.Value.Scalar.newBuilder().setValue(value);
  }

  @Override
  public void offerRescinded(SchedulerDriver schedulerDriver,
                             Protos.OfferID offerID) {
    System.out.println("This offer's been rescinded. Tough luck, cowboy.");
  }

  @Override
  public void statusUpdate(SchedulerDriver schedulerDriver,
                           Protos.TaskStatus taskStatus) {
    LOG.info("Status update: " + taskStatus.getState() + " from "
        + taskStatus.getTaskId().getValue());
    if (taskStatus.getState() == Protos.TaskState.TASK_FINISHED) {
      completedTaskCounter++;
      LOG.info("Number of completed tasks: " + completedTaskCounter + "/" + totalTaskCount);
    } else if (taskStatus.getState() == Protos.TaskState.TASK_FAILED
        || taskStatus.getState() == Protos.TaskState.TASK_LOST
        || taskStatus.getState() == Protos.TaskState.TASK_KILLED) {
      LOG.severe("Aborting because task " + taskStatus.getTaskId().getValue()
          + " is in unexpected state "
          + taskStatus.getState().getValueDescriptor().getName()
          + " with reason '"
          + taskStatus.getReason().getValueDescriptor().getName() + "'"
          + " from source '"
          + taskStatus.getSource().getValueDescriptor().getName() + "'"
          + " with message '" + taskStatus.getMessage() + "'");
    }

    if (totalTaskCount == completedTaskCounter) {
      LOG.info("All tasks are finished. Stopping driver");
      schedulerDriver.stop();
    }

  }
  @Override
  public void frameworkMessage(SchedulerDriver schedulerDriver,
                               Protos.ExecutorID executorID, Protos.SlaveID slaveID, byte[] bytes) {
    System.out.println("Received message (scheduler): " + new String(bytes)
        + " from " + executorID.getValue());
  }

  @Override
  public void disconnected(SchedulerDriver schedulerDriver) {
    System.out.println("We got disconnected yo");
  }

  @Override
  public void slaveLost(SchedulerDriver schedulerDriver,
                        Protos.SlaveID slaveID) {
    System.out.println("Lost slave: " + slaveID);
  }

  @Override
  public void executorLost(SchedulerDriver schedulerDriver,
                           Protos.ExecutorID executorID, Protos.SlaveID slaveID, int i) {
    LOG.severe("Lost executor on slave " + slaveID);
  }

  @Override
  public void error(SchedulerDriver schedulerDriver, String s) {
    System.out.println("We've got errors, man: " + s);
  }
}
