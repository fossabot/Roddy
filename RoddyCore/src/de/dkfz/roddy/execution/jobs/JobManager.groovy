/*
 * Copyright (c) 2017 eilslabs.
 *
 * Distributed under the MIT License (license terms are at https://www.github.com/eilslabs/Roddy/LICENSE.txt).
 */

package de.dkfz.roddy.execution.jobs

import de.dkfz.roddy.config.ResourceSet
import de.dkfz.roddy.Roddy
import de.dkfz.roddy.core.RuntimeService
import de.dkfz.roddy.knowledge.files.BaseFile
import sun.reflect.generics.reflectiveObjects.NotImplementedException

/**
 * (Proxy) Compatibility class for 2.3.x until 2.4
 * Created by heinold on 06.03.17.
 */
@Deprecated()
class JobManager {

    private BatchEuphoriaJobManager jobManager
    
    static JobManager getInstance() {
        return new JobManager(Roddy.getJobManager())
    }

    JobManager(BatchEuphoriaJobManager jobManager) {
        this.jobManager = jobManager
    }

    static String createJobName(BaseFile baseFile, String toolID, boolean reduceLevel) {
        return RuntimeService._createJobName(baseFile.executionContext, baseFile, toolID, reduceLevel)
    }

    Command createCommand(GenericJobInfo jobInfo) {
        BEJob job = new Job(jobInfo)
        jobManager.createCommand(job, jobInfo.tool, jobInfo.parentJobIDs)
    }

    de.dkfz.roddy.execution.jobs.JobResult runJob(Job job, boolean runDummy) {
        jobManager.runJob(job, runDummy)
    }

    ProcessingCommands convertResourceSet(ResourceSet resourceSet) {
        jobManager.convertResourceSet(resourceSet)
    }

    ProcessingCommands parseProcessingCommands(String alignmentProcessingOptions) {
        jobManager.parseProcessingCommands(alignmentProcessingOptions)
    }

    ProcessingCommands extractProcessingCommandsFromToolScript(File file) {
        jobManager.extractProcessingCommandsFromToolScript(file)
    }

    Job parseToJob(String commandString) {
        jobManager.parseToJob(commandString)
    }

    GenericJobInfo parseGenericJobInfo(String command) {
        jobManager.parseGenericJobInfo(command)
    }

    de.dkfz.roddy.execution.jobs.JobResult convertToArrayResult(Job arrayChildJob, de.dkfz.roddy.execution.jobs.JobResult parentJobsResult, int arrayIndex) {
        jobManager.convertToArrayResult(arrayChildJob, parentJobsResult, arrayIndex)
    }

    void storeJobStateInfo(Job job) {
//        jobManager.store
        throw new NotImplementedException()
    }

    void updateJobStatus() {
        jobManager.updateJobStatus()
    }

    void addJobStatusChangeListener(Job job) {
        jobManager.addJobStatusChangeListener(job)
    }

    String getLogFileWildcard(Job job) {
        jobManager.getLogFileWildcard(job)
    }

    boolean compareJobIDs(String jobID, String id) {
        jobManager.compareJobIDs(jobID, id)
    }

    String getStringForQueuedJob() {
        jobManager.getStringForQueuedJob()
    }

    String getStringForJobOnHold() {
        jobManager.getStringForJobOnHold()
    }

    String getStringForRunningJob() {
        jobManager.getStringForRunningJob()
    }

    String getSpecificJobIDIdentifier() {
        jobManager.getJobIdVariable()
    }

    String getSpecificJobArrayIndexIdentifier() {
        throw new NotImplementedException()
    }

    String[] peekLogFile(Job job) {
        jobManager.peekLogFile(job)
    }

    String parseJobID(String commandOutput) {
        jobManager.parseJobID(commandOutput)
    }

    String getSubmissionCommand() {
        jobManager.getSubmissionCommand()
    }

    void queryJobAbortion(List executedJobs) {
        jobManager.queryJobAbortion(executedJobs)
    }

    Map<String, JobState> queryJobStatus(List jobIDs) {
        jobManager.queryJobStatus(jobIDs)
    }

    Command createCommand(Job job, String jobName, List processingCommands, File tool, Map jobParameters, List parentJobs) {
        jobManager.createCommand(job, jobName, processingCommands, tool, jobParameters, parentJobs)
    }

    boolean executesWithoutJobSystem() {
        return jobManager.executesWithoutJobSystem()
    }
}
