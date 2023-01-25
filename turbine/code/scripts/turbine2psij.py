import psij
import sys
import argparse
import os

# Get swift-t parameters from the command line


parser = argparse.ArgumentParser()

parser.add_argument("--PROCS", help="Number of processes to use" , default=os.environ.get('PROCS', None ))
parser.add_argument("--PPN", help="Number of processes per node", default=os.environ.get('PPN', None ))
parser.add_argument(
    "--PROJECT", help="The project name to use with the system scheduler", default=os.environ.get('PROJECT', None ))
parser.add_argument("--QUEUE", help="Name of queue in which to run", default=os.environ.get('QUEUE', None ))
parser.add_argument(
    "--WALLTIME", help="Wall time argument to pass to scheduler, typically HH:MM:SS", default=os.environ.get('WALLTIME', None ))
parser.add_argument(
    "--TURBINE_OUTPUT", 
    help="The run directory for the workflow. Turbine will create this directory if it does not exist. If unset, a default value is automatically set. The TIC file is copied here before execution. Normally, this is unique to a Swift/T workflow execution, and starts out empty.",
    default=os.environ.get('TURBINE_OUTPUT', None ))

parser.add_argument("--TURBINE_OUTPUT_ROOT",
                    help="Directory under which Turbine will automatically create TURBINE_OUTPUT if necessary",
                    default=os.environ.get('TURBINE_OUTPUT_ROOT', None )
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
parser.add_argument("--TURBINE_STDOUT", default=os.environ.get('TURBINE_STDOUT', None ))
parser.add_argument("--TURBINE_LOG", default=os.environ.get('TURBINE_LOG', None ))
parser.add_argument("--TURBINE_DEBUG", default=os.environ.get('TURBINE_DEBUG', None ))
parser.add_argument("--ADLB_DEBUG", default=os.environ.get('ADLB_DEBUG', None ))
parser.add_argument("--ADLB_TRACE", default=os.environ.get('ADLB_TRACE', None ))
parser.add_argument("--MPI_LABEL", default=os.environ.get('MPI_LABEL', None ))
parser.add_argument("--TURBINE_WORKERS", default=os.environ.get('TURBINE_WORKERS', None ))
parser.add_argument("--ADLB_SERVERS", default=os.environ.get('ADLB_SERVERS', None ))

parser.add_argument(
    "--executable", help='program to be executed', default=os.environ.get('PSIJ_EXECUTABLE', None ) )
parser.add_argument(
    "--arguments", help="list of arguments passed to the executable", default=os.environ.get('PSIJ_ARGUMENTS', [] ), nargs="*")
parser.add_argument("--executor", help="Batch submission system", default=os.environ.get('PSIJ_EXECUTOR', "slurm" ),
                    choices=['slurm', 'pbs', 'batch'])
parser.add_argument("--debug", action='store_true' , default=os.environ.get('PSIJ_DEBUG', None ))

args = parser.parse_args()


if args.debug:
    print(args)
    sys.exit()


if not args.executable:
    print("Missing command to be executed. Aborting.")
    sys.exit()

# parser.add_argument("


jex = psij.JobExecutor.get_instance(args.executor)

job = psij.Job()


# Create job specification
spec = psij.JobSpec()

# Job
spec.name(args.TURBINE_JOBNAME)

if args.executable:
    spec.executable = args.executable
else:
    # exit
    sys.exit('127')

spec.directory = args.TURBINE_OUTPUT if args.TURBINE_OUTPUT else None

spec.arguments = args.arguments if args.arguments else None


# Attributs
# duration (timedelta) – Specifies the duration (walltime) of the job. A job whose execution exceeds its walltime can be terminated forcefully.

# queue_name (Optional[str]) – If a backend supports multiple queues, this parameter can be used to instruct the backend to send this job to a particular queue.

# project_name (Optional[str]) – If a backend supports multiple projects for billing purposes, setting this attribute instructs the backend to bill the indicated project for the resources consumed by this job.

# reservation_id (Optional[str]) – Allows specifying an advanced reservation ID. Advanced reservations enable the pre-allocation of a set of resources/compute nodes for a certain duration such that jobs can be run immediately, without waiting in the queue for resources to become available.

# custom_attributes (Optional[Dict[str, object]]) – Specifies a dictionary of custom attributes. Implementations of JobExecutor define and are responsible for interpreting custom attributes.


if args.PROJECT:
    spec.attributes.project_name = args.PROJECT
if args.QUEUE:
    spec.attributes.queue_name = args.QUEUE


spec.stdout_path = "out.txt"

# set node count
resource = psij.ResourceSpecV1()
spec.resources = resource

resource.node_count = int(args.PROCS / args.PPN) + 1

job.spec = spec

print(spec)

# Submit Job
# jex.submit(job)
