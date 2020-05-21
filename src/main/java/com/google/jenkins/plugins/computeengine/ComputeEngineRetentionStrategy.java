/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.computeengine;

import com.google.common.collect.ImmutableList;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.slaves.RetentionStrategy;
import java.util.List;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import lombok.extern.java.Log;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

/**
 * A strategy that allows: - setting one shot instances {@link OnceRetentionStrategy} - in case of
 * preemption of GCP instance to restart preempted tasks
 */
@Log
public class ComputeEngineRetentionStrategy extends RetentionStrategy<ComputeEngineComputer>
    implements ExecutorListener {
  private final OnceRetentionStrategy delegate;
  private final boolean oneShot;

  /**
   * Creates the retention strategy.
   *
   * @param retentionTimeMinutes Number of minutes of idleness after which to kill the slave; serves
   *     a backup in case the strategy fails to detect the end of a task.
   * @param oneShot Create one shot instance strategy.
   */
  ComputeEngineRetentionStrategy(int retentionTimeMinutes, boolean oneShot) {
    this.oneShot = oneShot;
    delegate = new OnceRetentionStrategy(retentionTimeMinutes);
  }

  @Override
  public long check(ComputeEngineComputer c) {
    return delegate.check(c);
  }

  @Override
  public void start(ComputeEngineComputer c) {
    delegate.start(c);
  }

  @Override
  public void taskAccepted(Executor executor, Queue.Task task) {
    if (oneShot) {
      // When a oneshot instance is used only one task is run, so better not accept more.
      synchronized (this) {
        ComputeEngineComputer computer = (ComputeEngineComputer) executor.getOwner();
        if (computer.isAcceptingTasks()) {
          computer.setAcceptingTasks(false);
          delegate.taskAccepted(executor, task);
        }
      }
    }
  }

  @Override
  public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
    if (wasPreempted(executor)) {
      rescheduleTask(task);
    }
    if (oneShot) {
      delegate.taskCompleted(executor, task, durationMS);
    }
  }

  @Override
  public void taskCompletedWithProblems(
      Executor executor, Queue.Task task, long durationMS, Throwable problems) {
    if (wasPreempted(executor)) {
      rescheduleTask(task);
    }
    if (oneShot) {
      delegate.taskCompletedWithProblems(executor, task, durationMS, problems);
    }
  }

  private Queue.Task getBaseTask(Queue.Task task) {
    Queue.Task parent = task.getOwnerTask();
    while (task != parent) {
      task = parent;
      parent = task.getOwnerTask();
    }
    return parent;
  }

  private boolean wasPreempted(Executor executor) {
    ComputeEngineComputer computer = (ComputeEngineComputer) executor.getOwner();
    final boolean preempted = computer.getPreempted();
    return preempted;
  }

  private void rescheduleTask(Queue.Task task) {
    Queue.Task baseTask = getBaseTask(task);
    log.log(Level.INFO, baseTask + " was preempted, rescheduling");
    List<Action> actions = generateActionsForTask(task);
    try (ACLContext notUsed = ACL.as(task.getDefaultAuthentication())) {
      Jenkins.get().getQueue().schedule2(baseTask, 0, actions);
    }
  }

  private List<Action> generateActionsForTask(Queue.Task task) {
    Queue.Task baseTask = getBaseTask(task);
    try {
      final Job job = (Job) baseTask;
      final List causes = job.getLastBuild().getCauses();
      log.log(Level.FINE, "Original causes: " + causes);
    } catch (Exception e) {
      log.log(Level.WARNING, "Exception for " + baseTask, e);
    }
    return ImmutableList.of(
        new CauseAction(new Cause.UserIdCause()), new CauseAction(new RebuildCause()));
  }

  public static class RebuildCause extends Cause {
    @Override
    public String getShortDescription() {
      return Messages.RebuildCause_ShortDescription();
    }
  }
}
