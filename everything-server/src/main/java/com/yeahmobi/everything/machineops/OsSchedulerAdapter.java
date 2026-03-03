package com.yeahmobi.everything.machineops;

import java.util.List;

/**
 * Cross-platform scheduler adapter.
 */
public interface OsSchedulerAdapter {

    CliScheduleResult createJob(String name, String command, String triggerSpec);

    List<CliScheduleJob> listJobs();

    CliScheduleResult pauseJob(String jobId);

    CliScheduleResult deleteJob(String jobId);

    CliScheduleResult runNow(String jobId);
}
