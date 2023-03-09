import psij
import sys
import argparse
import os
import datetime
from datetime import timedelta
import pathlib
from pathlib import Path

# Get swift-t parameters from the command line


parser = argparse.ArgumentParser()

parser.add_argument("--PROCS", help="Number of processes to use" , type=int , default=os.environ.get('PROCS', None ))
parser.add_argument("--PPN", help="Number of processes per node", type=int , default=os.environ.get('PPN', None ))
parser.add_argument(
    "--PROJECT", help="The project name to use with the system scheduler", default=os.environ.get('PROJECT', None ))
parser.add_argument("--QUEUE", help="Name of queue in which to run", default=os.environ.get('QUEUE', None ))
parser.add_argument(
    "--WALLTIME", help="Wall time argument to pass to scheduler, typically HH:MM:SS", default=os.environ.get('WALLTIME', None ))
parser.add_argument(
    "--TURBINE_OUTPUT", 
    help="The run directory for the workflow. Turbine will create this directory if it does not exist. If unset, a default value is automatically set. The TIC file is copied here before execution. Normally, this is unique to a Swift/T workflow execution, and starts out empty.",
    default=os.environ.get('TURBINE_OUTPUT', None ), 
    type=pathlib.Path )

parser.add_argument("--TURBINE_OUTPUT_ROOT",
                    help="Directory under which Turbine will automatically create TURBINE_OUTPUT if necessary",
                    default=os.environ.get('TURBINE_OUTPUT_ROOT', None ), type=pathlib.Path
                    )
parser.add_argument("--TURBINE_OUTPUT_FORMAT",
                    help="Allows customization of the automatic output directory creation. See Turbine output",
                    default=os.environ.get('TURBINE_OUTPUT_FORMAT', None ))
parser.add_argument(
    "--TURBINE_BASH_L", 
    default=os.environ.get('TURBINE_BASH_L', 0 ), 
    help="By default, Swift/T creates a Bash script for job submission that will be invoked with #!/bin/bash -l . Set TURBINE_BASH_L=0 to run with #!/bin/bash . This can avoid problems with environment modules on certain systems.")
parser.add_argument(
    "--TURBINE_DIRECTIVE", 
    help="Paste the given text into the submit script just after the scheduler directives. Allows users to insert, e.g., reservation information into the script. For example, on PBS, this text will be inserted just after the last default #PBS .",
    default=os.environ.get('TURBINE_DIRECTIVE', None )
    )
parser.add_argument(
    "--TURBINE_PRELAUNCH", 
    help="Paste the given text into the submit script. Allows users to insert, e.g., module load statements into the script. These shell commands will be inserted just before the execution is launched via mpiexec, aprun, or equivalent.",
    default=os.environ.get('TURBINE_PRELAUNCH', None )
    )

parser.add_argument("--TURBINE_SBATCH_ARGS",
                    help="Optional arguments passed to sbatch. These arguments may include --exclusive and --constraint=…, etc. Supported systems: slurm.",
                    default=os.environ.get('TURBINE_SBATCH_ARGS', None )
                    )


parser.add_argument(
    "--MAIL_ENABLED", help="If 1, send email on job completion.", default=os.environ.get('MAIL_ENABLED', None ))
parser.add_argument(
    "--MAIL_ADDRESS", help="If MAIL_ENABLED, send the email to the given address.", default=os.environ.get('MAIL_ADDRESS', None ))


parser.add_argument("--TURBINE_JOBNAME", default=os.environ.get('TURBINE_JOBNAME', None ))
parser.add_argument("--TURBINE_STDOUT", type=pathlib.Path, default=os.environ.get('TURBINE_STDOUT', None ))
parser.add_argument("--TURBINE_LOG", default=os.environ.get('TURBINE_LOG', None ))
parser.add_argument("--TURBINE_DEBUG", default=os.environ.get('TURBINE_DEBUG', None ))
parser.add_argument("--ADLB_DEBUG", default=os.environ.get('ADLB_DEBUG', None ))
parser.add_argument("--ADLB_TRACE", default=os.environ.get('ADLB_TRACE', None ))
parser.add_argument("--MPI_LABEL", default=os.environ.get('MPI_LABEL', None ))
parser.add_argument("--TURBINE_WORKERS", default=os.environ.get('TURBINE_WORKERS', None ))
parser.add_argument("--ADLB_SERVERS", default=os.environ.get('ADLB_SERVERS', None ))

parser.add_argument(
    "--executable", help='program to be executed', default=os.environ.get('TCLSH', None ) )
parser.add_argument(
    "--arguments", help="list of arguments passed to the executable", default=[os.environ.get('PROGRAM', None)], nargs="*")
parser.add_argument("--executor", help="Batch submission system", default=os.environ.get('PSIJ_EXECUTOR', "slurm" ),
                    choices=['slurm', 'pbs', 'batch'])
parser.add_argument("--debug", action='store_true' , default=os.environ.get('PSIJ_DEBUG', None ))

args = parser.parse_args()


if args.debug:
    print(args)
    # sys.exit()


if not args.executable:
    print(os.environ.get("SCRIPT"))
    print(os.environ.get("PROGRAM", None))
    print(os.environ.get("COMMAND"))
    print("Missing command to be executed. Aborting.")
    sys.exit()



jex = psij.JobExecutor.get_instance(args.executor)
job = psij.Job()


# Get Job Resource

# node_count (Optional[int]) If specified, request that the backend allocate this many compute nodes for the job.
# process_count (Optional[int]) If specified, instruct the backend to start this many process instances. This defaults to 1.
# processes_per_node (Optional[int]) Instruct the backend to run this many process instances on each node.
# cpu_cores_per_process (Optional[int]) Request this many CPU cores for each process instance. This property is used by a backend to calculate the number of nodes from the process_count
# gpu_cores_per_process (Optional[int]) 
# exclusive_node_use (bool)

resource = psij.ResourceSpecV1(
    node_count = None ,
    process_count = args.PROCS ,
    processes_per_node = args.PPN ,
    cpu_cores_per_process = None ,
    gpu_cores_per_process = None ,
    exclusive_node_use = False , # What is a good default ?
)




# Get Job Attributes

# Default WALLTIME is one minute 
(h,m,ss)=(0,1,0) 

# Parse time components from argument and create timedelta object
if args.WALLTIME :
    (h,m,s)=args.WALLTIME.split(";")
duration = timedelta(
    seconds=s,
    minutes:=m,
    hours=h
)

# set attributes

#### MISSING Partition #######

attributes = psij.JobAttributes(
    duration = duration ,
    queue_name = args.QUEUE ,
    project_name = args.PROJECT ,
    reservation_id = None ,
    custom_attributes = {} ,
)

# duration (timedelta) – Specifies the duration (walltime) of the job. A job whose execution exceeds its walltime can be terminated forcefully.
# queue_name (Optional[str]) – If a backend supports multiple queues, this parameter can be used to instruct the backend to send this job to a particular queue.
# project_name (Optional[str]) – If a backend supports multiple projects for billing purposes, setting this attribute instructs the backend to bill the indicated project for the resources consumed by this job.
# reservation_id (Optional[str]) – Allows specifying an advanced reservation ID. Advanced reservations enable the pre-allocation of a set of resources/compute nodes for a certain duration such that jobs can be run immediately, without waiting in the queue for resources to become available.
# custom_attributes (Optional[Dict[str, object]]) – Specifies a dictionary of custom attributes. Implementations of JobExecutor define and are responsible for interpreting custom attributes.






# Create job specification
spec = psij.JobSpec(
    name = args.TURBINE_JOBNAME ,
    executable = args.executable ,
    arguments = args.arguments ,
    directory = args.TURBINE_OUTPUT, # why not TURBINE_OUTPUT_ROOT ?
    inherit_environment = True , # check with Justin
    environment = {} ,
    stdin_path = None ,
    stdout_path = args.TURBINE_STDOUT ,
    stderr_path = args.TURBINE_OUTPUT / "/stderr.log" ,
    resources = resource , # HERE comes the MPI stuff etc
    attributes = None , # Empty for initial draft
    pre_launch = None ,
    post_launch = None ,
    launcher = "mpirun"   
)


job.spec = spec
print(spec)

# Submit Job
jex.submit(job)

# status = job.wait(timedelta(seconds=60))  # 3 sec should be plenty in this case
# if status is None:
#     raise RuntimeError("Job did not complete")
# if status.exit_code != 0:
#     raise RuntimeError(f"Job failed with status {status}")
# with output_path.open("r") as fd:
#     assert socket.gethostname() in fd.read()
