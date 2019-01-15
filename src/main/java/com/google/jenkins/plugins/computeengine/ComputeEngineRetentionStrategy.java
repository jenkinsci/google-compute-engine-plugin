package com.google.jenkins.plugins.computeengine;

import com.google.common.collect.ImmutableList;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Executor;
import hudson.model.ExecutorListener;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        if (oneShot) {
            delegate.taskAccepted(executor, task);
        }
    }

    @Override
    public void taskCompleted(Executor executor, Queue.Task task, long durationMS) {
        checkPreempted(executor, task);
        if (oneShot) {
            delegate.taskCompleted(executor, task, durationMS);
        }
    }

    @Override
    public void taskCompletedWithProblems(Executor executor, Queue.Task task, long durationMS, Throwable problems) {
        checkPreempted(executor, task);
        if (oneShot) {
            delegate.taskCompletedWithProblems(executor, task, durationMS, problems);
        }
    }

    private Queue.Task getBaseTask(Queue.Task task) {
        Queue.Task parent = task.getOwnerTask();
        while (task != parent) {
            System.out.println("Task next: " + parent);
            task = parent;
            parent = task.getOwnerTask();
        }
        return parent;
    }

    private void checkPreempted(Executor executor, Queue.Task task) {
        ComputeEngineComputer computer = (ComputeEngineComputer) executor.getOwner();
        final boolean preemptible = computer.getPreemptible();
        final boolean preempted = computer.getPreempted();
        Queue.Task baseTask = getBaseTask(task);
        try {
            final List causes = ((Job) baseTask).getLastBuild().getCauses();
            System.out.println("Causes: " + causes);
        } catch (Exception e) {
            System.out.println("Exception for " + baseTask);
            e.printStackTrace();
        }
        if (preemptible && preempted) {
            LOGGER.log(Level.INFO, baseTask + " preemptible " + preemptible + " && preempted " + preempted);
            List<Action> actions = generateActionsForTask(task);
            Jenkins.getInstance().getQueue().schedule2(baseTask, 0, actions);
        } else if (preemptible) {
            LOGGER.log(Level.INFO, "preemptible " + preemptible + " && preempted " + preempted);
        }
    }

    private List<Action> generateActionsForTask(Queue.Task task) {
        return ImmutableList.of(new CauseAction(new Cause.UserIdCause()),
                new CauseAction(new Cause() {
                    @Override
                    public String getShortDescription() {
                        return "Rebuilding preemptied job";
                    }
                }));
    }
    
    
} 
