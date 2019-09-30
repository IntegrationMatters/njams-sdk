package com.im.sdk.njams.sample;

import com.im.njams.sdk.Njams;
import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

@Path("logmessage")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class LogMessageResource {
    private static final Logger LOG = LoggerFactory.getLogger(LogMessageResource.class);

    @POST
    @Path("start")
    public String createLogMessage() {
        Njams njams = NjamsStartup.njams;
        com.im.njams.sdk.common.Path processPath = new com.im.njams.sdk.common.Path("Processes", "SimpleProcess");
        ProcessModel process = njams.getProcessModel(processPath);

        Job job = process.createJob();
        // Starts the job, i.e., sets the according status, job start date if not set before, and flags the job to begin
        // flushing.
        job.start();
        ActivityModel startModel = process.getStartActivities().get(0);
        job.createActivity(startModel).build();
        LOG.info("Start Logmessage with ID " + job.getJobId());

        return job.getJobId();
    }

    @POST
    @Path("{jobId}/{fromActivity}/{toActivity}")
    public String createLogMessage(@PathParam("jobId") String jobId, @PathParam("fromActivity") String fromActivity,
                                   @PathParam("toActivity") String toActivity) {

        Njams njams = NjamsStartup.njams;
        com.im.njams.sdk.common.Path processPath = new com.im.njams.sdk.common.Path("Processes", "SimpleProcess");
        ProcessModel process = njams.getProcessModel(processPath);

        Job job = njams.getJobById(jobId);
        if(job == null) {
            String warning = "Running Logmessage with ID " + jobId + " not found.";
            LOG.warn(warning);
            return warning;
        }

        // step to the next activity from the previous one.
        Activity fromActivityInstance = job.getActivityByModelId(fromActivity);
        ActivityModel toActivityModel = process.getActivity(toActivity);
        Activity toActivityInstance = fromActivityInstance.stepTo(toActivityModel).build();
        toActivityInstance.processInput("Activity input");
        toActivityInstance.processOutput("Activity output");

        LOG.info("Stepped from " + fromActivityInstance.getInstanceId() + "to" + toActivityInstance.getInstanceId());

        // End the job, which will flush all previous steps into a logmessage wich will be send to the server
        if (toActivity.equals("end")) {
            job.end();
            LOG.info("End Logmessage with ID:" + jobId);
        }

        return job.getJobId();
    }
}
