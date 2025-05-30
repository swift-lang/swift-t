
"""
TURBINE 2 PSI/J

Turbine scheduler script for PSI/J
https://exaworks.org/psij-python

Gets Swift/T job settings from the command line or environment.
Submits job.
Waits for job completion if directed to do so.

Turbine scheduler environment variables are documented here:
http://swift-lang.github.io/swift-t/sites.html#variables
"""

import argparse
from datetime import timedelta
import pathlib
import sys
import os
import time

def msg(t):
    print("turbine2psij: " + t)

try:
    import psij
    from psij.executors.batch.batch_scheduler_executor import BatchSchedulerExecutorConfig
except:
    msg("PSI/J could not be imported!")
    msg("Install PSI/J into the Python used by Swift/T")
    exit(1)

# PSI/J polling interval in seconds:
polling_interval = 10

parser = argparse.ArgumentParser()

# Basic queue settings:
parser.add_argument("--PROCS",
                    help="Number of processes to use",
                    type=int, default=os.environ.get("PROCS", None))
parser.add_argument("--PPN",
                    help="Number of processes per node",
                    type=int, default=os.environ.get("PPN", None))
parser.add_argument("--PROJECT",
                    help="The project name to use with the system scheduler",
                    default=os.environ.get("PROJECT", None))
parser.add_argument("--QUEUE",
                    help="Name of queue in which to run",
                    default=os.environ.get("QUEUE", None))
parser.add_argument("--WALLTIME",
                    help="""Wall time argument to pass to scheduler as
                            'HH:MM:SS'""",
                    default=os.environ.get("WALLTIME", None ))
parser.add_argument("--TURBINE_JOBNAME",
                    default=os.environ.get("TURBINE_JOBNAME", None))

# Turbine output directory settings
parser.add_argument("--TURBINE_OUTPUT",
                    help="""The run directory for the
                            workflow. Turbine will create this
                            directory if it does not exist. If unset,
                            a default value is automatically set. The
                            TIC file is copied here before
                            execution. Normally, this is unique to a
                            Swift/T workflow execution, and starts out
                            empty.
                    """,
                    type=pathlib.Path,
                    default=os.environ.get("TURBINE_OUTPUT", None))
parser.add_argument("--TURBINE_OUTPUT_ROOT",
                    help="""Directory under which Turbine will
                            automatically create TURBINE_OUTPUT if
                            necessary.
                    """,
                    type=pathlib.Path,
                    default=os.environ.get('TURBINE_OUTPUT_ROOT', None))
parser.add_argument("--TURBINE_OUTPUT_FORMAT",
                    help="""Allows customization of the automatic
                            output directory creation.  See
                            TURBINE_OUTPUT.
                    """,
                    default=os.environ.get("TURBINE_OUTPUT_FORMAT", None))

# Script manipulation settings:
parser.add_argument("--TURBINE_BASH_L",
                    help="""By default, Swift/T creates a Bash script
                            for job submission that will be invoked
                            with #!/bin/bash -l . Set TURBINE_BASH_L=0
                            to run with #!/bin/bash . This can avoid
                            problems with environment modules on
                            certain systems.
                    """,
                    default=os.environ.get("TURBINE_BASH_L", 0))
parser.add_argument("--TURBINE_DIRECTIVE",
                    help="""Paste the given text into the submit
                            script just after the scheduler
                            directives. Allows users to insert, e.g.,
                            reservation information into the
                            script. For example, on PBS, this text
                            will be inserted just after the last
                            default #PBS .""",
                    default=os.environ.get("TURBINE_DIRECTIVE", None))

parser.add_argument("--TURBINE_PRELAUNCH",
                    help="""Paste the given text into the submit
                            script. Allows users to insert, e.g.,
                            module load statements into the
                            script. These shell commands will be
                            inserted just before the execution is
                            launched via mpiexec, aprun, or
                            equivalent.
                    """,
                    default=os.environ.get("TURBINE_PRELAUNCH", None ))

parser.add_argument("--TURBINE_SBATCH_ARGS",
                    help="""Optional arguments passed to sbatch.
                            These arguments may include --exclusive
                            and --constraint=…, etc. Supported
                            systems: slurm.
                    """,
                    default=os.environ.get('TURBINE_SBATCH_ARGS', None))

# Email settings:
parser.add_argument("--MAIL_ENABLED",
                    help="If 1, send email on job completion.",
                    default=os.environ.get('MAIL_ENABLED', None))
parser.add_argument("--MAIL_ADDRESS",
                    help="""If MAIL_ENABLED, send the email to the
                            given address.
                    """,
                    default=os.environ.get('MAIL_ADDRESS', None))

# Output and logging settings:
parser.add_argument("--TURBINE_STDOUT",
                    type=pathlib.Path,
                    default=os.environ.get("TURBINE_STDOUT", None))
parser.add_argument("--TURBINE_LOG",
                    default=os.environ.get("TURBINE_LOG", None))
parser.add_argument("--TURBINE_DEBUG",
                    default=os.environ.get("TURBINE_DEBUG", None))
parser.add_argument("--ADLB_DEBUG",
                    default=os.environ.get("ADLB_DEBUG", None))
parser.add_argument("--ADLB_TRACE",
                    default=os.environ.get("ADLB_TRACE", None ))
parser.add_argument("--MPI_LABEL",
                    default=os.environ.get("MPI_LABEL", None))
parser.add_argument("--TURBINE_WORKERS",
                    default=os.environ.get("TURBINE_WORKERS", None))
parser.add_argument("--ADLB_SERVERS",
                    default=os.environ.get("ADLB_SERVERS", None))

parser.add_argument("--executable",
                    help="program to be executed",
                    default=os.environ.get("TCLSH", None))
parser.add_argument("--arguments",
                    help="list of arguments passed to the executable",
                    default=[os.environ.get("PROGRAM", None)], nargs="*")
parser.add_argument("--executor",
                    help="Batch submission system",
                    default=os.environ.get("PSIJ_EXECUTOR", "slurm"),
                    choices=["slurm", "pbs", "batch"])
parser.add_argument("--debug", action="store_true",
                    help="Turn on debugging",
                    default=os.environ.get("PSIJ_DEBUG", None))

args = parser.parse_args()

if args.debug:
    msg("args: start...")
    for key, value in vars(args).items():
        print("%-22s %s" % (key+":", value))
    msg("args: stop.")

if args.executable is None:
    print(os.environ.get("SCRIPT"))
    print(os.environ.get("PROGRAM", None))
    print(os.environ.get("COMMAND"))
    print("Missing command to be executed. Aborting.")
    sys.exit()


# Construct a Job:
logfile = args.TURBINE_OUTPUT / "psij.log"
if args.debug:
    msg("log: " + str(logfile))
cfg = BatchSchedulerExecutorConfig(launcher_log_file=logfile,
                                   work_directory=args.TURBINE_OUTPUT,
                                   queue_polling_interval=polling_interval)
jex = psij.JobExecutor.get_instance(args.executor, config=cfg)
job = psij.Job()

# Get job resources

# node_count (Optional[int]) If specified, request that the backend
# allocate this many compute nodes for the job.

# process_count (Optional[int]) If specified, instruct the backend to
# start this many process instances. This defaults to 1.

# processes_per_node (Optional[int]) Instruct the backend to run this
# many process instances on each node.

# cpu_cores_per_process (Optional[int]) Request this many CPU cores
# for each process instance. This property is used by a backend to
# calculate the number of nodes from the process_count

# gpu_cores_per_process (Optional[int])

# exclusive_node_use (bool)

resource = psij.ResourceSpecV1(
    node_count = None,
    process_count = args.PROCS,
    processes_per_node = args.PPN,
    cpu_cores_per_process = None,
    gpu_cores_per_process = None,
    exclusive_node_use = False
)

# Get Job Attributes

# Default WALLTIME is one minute
(h,m,ss) = (0,1,0)

# Parse time components from argument and create timedelta object
if args.WALLTIME is not None:
    (h,m,s) = args.WALLTIME.split(":")
duration = timedelta(
    seconds=int(s),
    minutes=int(m),
    hours=int(h)
)

# Set attributes
attributes = psij.JobAttributes(
    duration = duration,
    queue_name = args.QUEUE,
    account = args.PROJECT,
    reservation_id = None,
    custom_attributes = {},
)

msg(str(attributes))

# duration (timedelta) - Specifies the duration (walltime) of the
# job. A job whose execution exceeds its walltime can be terminated
# forcefully.

# queue_name (Optional[str]) - If a backend supports multiple queues,
# this parameter can be used to instruct the backend to send this job
# to a particular queue.

# project_name (Optional[str]) - If a backend supports multiple
# projects for billing purposes, setting this attribute instructs the
# backend to bill the indicated project for the resources consumed by
# this job.

# reservation_id (Optional[str]) - Allows specifying an advanced
# reservation ID. Advanced reservations enable the pre-allocation of a
# set of resources/compute nodes for a certain duration such that jobs
# can be run immediately, without waiting in the queue for resources
# to become available.

# custom_attributes (Optional[Dict[str, object]]) - Specifies a
# dictionary of custom attributes. Implementations of JobExecutor
# define and are responsible for interpreting custom attributes.

# Create job specification
spec = psij.JobSpec(
    name = args.TURBINE_JOBNAME,
    executable = os.environ.get("TURBINE_HOME") + "/bin/turbine-pilot",
    arguments = args.arguments,
    directory = args.TURBINE_OUTPUT,
    inherit_environment = True,
    environment = {},
    stdin_path = None,
    stdout_path = args.TURBINE_OUTPUT / "output.txt",
    stderr_path = args.TURBINE_OUTPUT / "stderr.txt",
    resources = resource,
    attributes = attributes,
    pre_launch = None,
    post_launch = None,
    launcher = "mpirun"
)

if args.debug:
    msg(str(spec))

job.spec = spec

# Submit Job!
jex.submit(job)

msg("job submitted: ID: " + job.native_id)

# Check if we are waiting for job completion:
w = os.environ.get("WAIT_FOR_JOB", "0")
if int(w) == 0:
    # If not, we are done:
    exit()

# Wait for job completion
msg("waiting for job completion...")
# Give PSI/J time to make its first poll:
# https://github.com/ExaWorks/psij-python/issues/358
time.sleep(polling_interval * 2)
while True:
    status = job.wait(timedelta(seconds=polling_interval))
    if status is not None:
        msg("PSI/J job status: " + str(status.state))
        if status.final:
            break
    else:
        msg("PSI/J job status: None")

if status.exit_code != 0:
    msg("PSI/J job failed with exit code: %i" % status)
    exit(status.exit_code)

msg("OK.")
