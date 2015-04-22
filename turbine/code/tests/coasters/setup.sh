export TCLLIBPATH=/homes/yadunand/swift-trunk/cog/modules/provider-coaster-c-client/tcl
export TURBINE_USER_LIB=$TCLLIBPATH
#export COASTER_SETTINGS="SLOTS=1,MAX_NODES=1,JOBS_PER_NODE=2,WORKER_MANAGER=passive"
export COASTER_SETTINGS="slots=1,maxNodes=1,jobsPerNode=2,QUEUE=westmere"
export COASTER_SERVICE_URL="127.0.0.1:53001"
export COASTER_JOBMANAGER="none:slurm"
