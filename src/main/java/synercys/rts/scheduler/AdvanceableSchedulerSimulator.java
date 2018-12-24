package synercys.rts.scheduler;

import synercys.rts.framework.Job;
import synercys.rts.framework.Task;
import synercys.rts.framework.TaskSet;
import synercys.rts.framework.event.EventContainer;
import synercys.rts.framework.event.SchedulerIntervalEvent;

import java.util.HashMap;

/**
 * AdvanceableSchedulerSimulator.java
 * Purpose: An abstract scheduler class that implements some of the basic methods for an advanceable scheduler simulator.
 *          An advanceable scheduler simulator allows users to call "advance()" to simulate by steps (and thus allow
 *          flexible simulation duration), rather than using "runSim()" to complete simulation for a given, fixed duration
 *          at a time.
 *
 * @author CY Chen (cchen140@illinois.edu)
 * @version 1.0 - 2018, 12/21
 */
abstract class AdvanceableSchedulerSimulator extends SchedulerSimulator {
    // This map stores each task's next job instance, no matter it's arrived or not.
    protected HashMap<Task, Job> nextJobOfATask = new HashMap<>();

    public AdvanceableSchedulerSimulator(TaskSet taskSet, boolean runTimeVariation, String schedulingPolicy) {
        super(taskSet, runTimeVariation, schedulingPolicy);

        /* Initialize the first job of each task. */
        initializeFirstTaskJobs();
    }

    abstract protected Job getNextJob(long tick);
    abstract protected Job getPreemptingJob(Job runJob);

    @Override
    public EventContainer runSim(long tickLimit) {
        tick = 0;

        while (tick <= tickLimit) {
            advance();
        }
        simEventContainer.trimEventsToTimeStamp(tickLimit);

        return simEventContainer;
    }

    /**
     * Run simulation and advance to next scheduling point.
     */
    public void advance() {
        Job currentJob = getNextJob(tick);

        // If it is a future job, then jump the tick first.
        if (currentJob.releaseTime > tick)
            tick = currentJob.releaseTime;

        // Run the job (and log the execution interval).
        tick = runJobToNextSchedulingPoint(tick, currentJob);
    }

    protected Job updateTaskJob(Task task) {
        /* Determine next arrival time. */
        long nextArrivalTime;
        if (task.isSporadicTask()) {
            nextArrivalTime = nextJobOfATask.get(task).releaseTime + getVariedInterArrivalTime(task);
        } else {
            nextArrivalTime = nextJobOfATask.get(task).releaseTime + task.getPeriod();
        }

        /* Determine the execution time. */
        long executionTime;
        if (runTimeVariation == true) {
            executionTime = getVariedExecutionTime(task);
        } else {
            executionTime = task.getWcet();
        }

        Job newJob = new Job(task, nextArrivalTime, executionTime);
        nextJobOfATask.put(task, newJob);

        return newJob;
    }

    protected void initializeFirstTaskJobs() {
        /* Note that the first job of a sporadic task arrives at the initial offset time point.
        * It is based on the assumption (also the fact) that any task needs to run some initialization
        * when it first starts. */
        for (Task task: taskSet.getRunnableTasksAsArray()) {
            Job firstJob;
            if (runTimeVariation == true)
                firstJob = new Job(task, task.getInitialOffset(), getVariedExecutionTime(task));
            else
                firstJob = new Job(task, task.getInitialOffset(), task.getWcet());
            nextJobOfATask.put(task, firstJob);
        }
    }

    protected Job getEarliestArrivedJob() {
        Job targetJob = null;
        long earliestNextReleaseTime = Long.MAX_VALUE;
        for (Job job: nextJobOfATask.values()) {
            if (job.releaseTime < earliestNextReleaseTime) {
                earliestNextReleaseTime = job.releaseTime;
                targetJob = job;
            }
        }
        return targetJob;
    }

    protected long runJobToNextSchedulingPoint(long tick, Job runJob) {
        /* Find if there is any job preempting the runJob. */
        Job earliestPreemptingJob = getPreemptingJob(runJob);

        // Check if any new job will preempt runJob.
        if (earliestPreemptingJob == null) {
            /* This job is finished. */
            long runJobFinishTime = tick + runJob.remainingExecTime;
            runJob.remainingExecTime = 0;

            /* Log the job interval. */
            SchedulerIntervalEvent currentJobEvent = new SchedulerIntervalEvent(tick, runJobFinishTime, runJob.task, "");
            if ( runJob.hasStarted == false ) { // Check this job's starting state.
                runJob.hasStarted = true;
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_START, SchedulerIntervalEvent.SCHEDULE_STATE_END);
            } else {
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_RESUME, SchedulerIntervalEvent.SCHEDULE_STATE_END);
            }
            simEventContainer.add(currentJobEvent);

            updateTaskJob(runJob.task);

            // No one will preempt runJob, so runJob is good to finish its job.
            return runJobFinishTime;
        } else {
            /* This job is preempted. */
            long earliestPreemptingJobReleaseTime = earliestPreemptingJob.releaseTime;

            // runJob will be preempted before it's finished, so update runJob's remaining execution time.
            runJob.remainingExecTime -= (earliestPreemptingJobReleaseTime - tick);

            /* Log the job interval. */
            SchedulerIntervalEvent currentJobEvent = new SchedulerIntervalEvent(tick, earliestPreemptingJobReleaseTime, runJob.task, "");
            if ( runJob.hasStarted == false ) { // Check this job's starting state.
                runJob.hasStarted = true;
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_START, SchedulerIntervalEvent.SCHEDULE_STATE_SUSPEND);
            } else {
                currentJobEvent.setScheduleStates(SchedulerIntervalEvent.SCHEDULE_STATE_RESUME, SchedulerIntervalEvent.SCHEDULE_STATE_SUSPEND);
            }
            simEventContainer.add(currentJobEvent);

            return earliestPreemptingJobReleaseTime;
        }
    }
}