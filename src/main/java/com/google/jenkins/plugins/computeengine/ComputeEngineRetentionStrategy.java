package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.jenkins.plugins.computeengine.client.ComputeClient.nameFromSelfLink;

public class ComputeEngineRetentionStrategy extends RetentionStrategy<ComputeEngineComputer> implements ExecutorListener {

    private static final Logger LOGGER = Logger.getLogger(ComputeEngineRetentionStrategy.class.getName());

    private final OnceRetentionStrategy delegate;
    private final boolean oneShot;

    /**
     * Creates the retention strategy.
     *
     * @param retentionTimeMinutes number of minutes of idleness after which to kill the slave; serves a backup in case the strategy fails to detect the end of a task
     * @param oneShot              create one shot instance strategy
     */
    ComputeEngineRetentionStrategy(int retentionTimeMinutes, boolean oneShot) {
        this.oneShot = oneShot;
        delegate = new OnceRetentionStrategy(retentionTimeMinutes);
    }

    @Override
    public long check(@Nonnull ComputeEngineComputer c) {
        return delegate.check(c);
    }

    @Override
    public void start(@Nonnull ComputeEngineComputer c) {
        delegate.start(c);
    }

    @Override
    public void taskAccepted(Executor executor, Queue.Task task) {
        LOGGER.log(Level.INFO, "Task accepted " + task);
        LOGGER.log(Level.INFO, "Task accepted owner " + getBaseTask(task));
        LOGGER.log(Level.INFO, "Task accepted sub" + task.getSubTasks());
        LOGGER.log(Level.INFO, "Task accepted res" + task.getResourceList());
        try {
            LOGGER.log(Level.INFO, "Task accepted res" + ((Run)getBaseTask(task)).getResult().toString());
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "tetts error", ex);
        }
        if (oneShot) {
            delegate.taskAccepted(executor, task);
        }
    }
    
    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        LOGGER.log(Level.INFO, "Task completed " + task + " executor " + executor);
        checkPre(executor, task);
        if (oneShot) {
            delegate.taskCompleted(executor, task, durationMS);
        }
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        LOGGER.log(Level.INFO, "Task completed with problems " + task + " executor " + executor);
        checkPre(executor, task);
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
    
    private void checkPre(Executor executor, Queue.Task task) {
        ComputeEngineComputer computer = (ComputeEngineComputer) executor.getOwner();
        final boolean preemptive = checkPreemptive(computer);
        LOGGER.log(Level.INFO, "pre " + preemptive);
        if (preemptive) {
            LOGGER.log(Level.INFO, "Trying to rerun task");
//                task.getOwnerTask().getOwnerTask().createExecutable().run();
//                List<Action> actions = ImmutableList.of(new CauseAction(new Cause.UpstreamCause(task));
            Jenkins.getInstance().getQueue().schedule2(task, 0);
        }
    }
    
    private boolean isFailed(Queue.Task task) {
        return false;
    }

    //"operationType": "compute.instances.preempted",
    //   "targetLink": "https://www.googleapis.com/compute/v1/projects/team-saasops/zones/us-central1-a/instances/slave-ingwar-pre-rg3jbi",
    //  Filter [(operationType="compute.instances.preempted") AND 
    // (targetLink="https://www.googleapis.com/compute/v1/projects/team-saasops/zones/us-central1-a/instances/slave-ingwar-pre-rg3jbi")]

    private boolean checkPreemptive(ComputeEngineComputer computer) {
        ComputeEngineCloud cloud = computer.getCloud();
        boolean preempted = false;
        try {
            final Instance instance = cloud.getClient().getInstance(cloud.getProjectId(), computer.getNode().zone, nameFromSelfLink(computer.getInstance().getSelfLink()));
            LOGGER.log(Level.WARNING, "instance " + instance);
            preempted = instance.getStatus().equals("TERMINATED");
//            preempted = computer.getCloud().getClient().isPreempted(cloud.getProjectId(), computer.getNode().zone, computer.getInstance().getSelfLink());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "checkPreemptive error", ex);
        }
        return preempted;
    }
}
