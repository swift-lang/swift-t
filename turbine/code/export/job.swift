
// JOB.SWIFT
// Various system-level configurations for application jobs.

@par @dispatch=WORKER
(int status) job_srun(int cores_per_job, int procs_per_job,
                      string cmd_line[])
  "turbine" "0.0" "job_srun_tcl";
