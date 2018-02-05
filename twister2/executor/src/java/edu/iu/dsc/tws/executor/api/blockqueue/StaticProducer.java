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
package edu.iu.dsc.tws.executor.api.blockqueue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;

import edu.iu.dsc.tws.executor.constants.TaskOps;
import edu.iu.dsc.tws.executor.model.Task;

/**
 * Created by vibhatha on 9/14/17.
 */
public class StaticProducer implements Serializable {

  /*
  *
  * int i = 0;
        for (Task task :taskList) {
          System.out.println("Task "+i+" executing");
          queue.put(produceTask(task));
          i++;
        }
  * */
  protected BlockingQueue queue = null;
  protected Task task = null;
  protected ArrayList<Task> taskList = null;
  protected TaskOps executableType= TaskOps.SINGLE;

  public StaticProducer(BlockingQueue queue, Task task, ArrayList<Task> taskList, TaskOps executableType) {
    this.queue = queue;
    this.task = task;
    this.taskList = taskList;
    this.executableType = executableType;
  }

  public StaticProducer(BlockingQueue queue, ArrayList<Task> taskList, TaskOps executableType) {
    this.queue = queue;
    this.taskList = taskList;
    this.executableType = executableType;
  }

  public BlockingQueue getQueue() {
    return queue;
  }

  public void setQueue(BlockingQueue queue) {
    this.queue = queue;
  }

  public Task getTask() {
    return task;
  }

  public void setTask(Task task) {
    this.task = task;
  }

  public ArrayList<Task> getTaskList() {
    return taskList;
  }

  public void setTaskList(ArrayList<Task> taskList) {
    this.taskList = taskList;
  }

  public TaskOps getExecutableType() {
    return executableType;
  }

  public void setExecutableType(TaskOps executableType) {
    this.executableType = executableType;
  }

  public Object produce(String taskDescriptor) throws InterruptedException {

    System.out.println("Producing : "+taskDescriptor);

    //this.queue.put(taskDescriptor);
    return new String("Task Id : "+taskDescriptor);
  }

  public Object produceTask(Task task){

    System.out.println("Producing Task : "+task.getName()+" : "+task.getThreadId());
    return task;
  }

  public int size(){
    return this.queue.size();
  }


  public void create() throws InterruptedException {

    if(executableType ==TaskOps.SINGLE){
      //queue.put(produce("Process : "+i+" - "+"Task Descriptor : "+i));
      queue.put(produceTask(this.task));
    }

    if(executableType == TaskOps.LIST){
      int i = 0;
      for (Task task :taskList) {
        System.out.println("Task "+i+" executing");
        queue.put(produceTask(task));
        i++;
      }

    }

    if(executableType == TaskOps.CONTINUES){

    }

  }


  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StaticProducer)) return false;

    StaticProducer that = (StaticProducer) o;

    if (getQueue() != null ? !getQueue().equals(that.getQueue()) : that.getQueue() != null)
      return false;
    if (getTask() != null ? !getTask().equals(that.getTask()) : that.getTask() != null)
      return false;
    if (getTaskList() != null ? !getTaskList().equals(that.getTaskList()) : that.getTaskList() != null)
      return false;
    return getExecutableType() == that.getExecutableType();
  }

  @Override
  public int hashCode() {
    int result = getQueue() != null ? getQueue().hashCode() : 0;
    result = 31 * result + (getTask() != null ? getTask().hashCode() : 0);
    result = 31 * result + (getTaskList() != null ? getTaskList().hashCode() : 0);
    result = 31 * result + (getExecutableType() != null ? getExecutableType().hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "StaticProducer{" +
        "queue=" + queue +
        ", task=" + task +
        ", taskList=" + taskList +
        ", executableType=" + executableType +
        '}';
  }
}
