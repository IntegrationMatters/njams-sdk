package com.im.njams.sdk.it.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.im.njams.sdk.logmessage.Activity;
import com.im.njams.sdk.logmessage.Job;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Drives {@code count} jobs against the fixed process model, each carrying a {@code payloadBytes}-sized business
 * data payload, across {@code concurrency} submitting threads. Returns each job's SDK-assigned {@code logId}, so
 * a scenario can assert exactly one delivered message per ID with no duplicates and no drops.
 */
public final class MessageDriver {

    private MessageDriver() {
    }

    public static List<String> run(ProcessModel model, int count, int payloadBytes, int concurrency)
        throws InterruptedException {
        String payload = "x".repeat(Math.max(0, payloadBytes));
        ActivityModel activityModel = model.getActivity(FixedProcessModel.ACTIVITY_MODEL_ID);

        if (concurrency <= 1) {
            List<String> logIds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                logIds.add(runOneJob(model, activityModel, payload));
            }
            return logIds;
        }

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<String>> futures = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                futures.add(pool.submit(() -> runOneJob(model, activityModel, payload)));
            }
            List<String> logIds = new ArrayList<>(count);
            for (Future<String> future : futures) {
                logIds.add(future.get(2, TimeUnit.MINUTES));
            }
            return logIds;
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("MessageDriver job failed", e);
        } finally {
            pool.shutdown();
        }
    }

    private static String runOneJob(ProcessModel model, ActivityModel activityModel, String payload) {
        Job job = model.createJob();
        job.start();
        Activity activity = job.activities().create(activityModel).addAttribute("payload", payload).build();
        activity.end();
        job.end(true);
        return job.getLogId();
    }
}
