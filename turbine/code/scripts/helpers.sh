
# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# HELPERS.SH
# Reusable general-purpose Bash (and ZSH) shell helper functions.

log_path()
# Pretty print a colon-separated variable
{
  echo ${1}:
  eval echo \$$1 | tr : '\n' | nl
}

date_nice()
# Human-readable date: minute resolution
{
  date "+%Y-%m-%d %H:%M"
}

date_nice_s()
# Human-readable date: second resolution
{
  date "+%Y-%m-%d %H:%M:%S"
}

date_path()
# E.g., 2006/10/13/14/26/12 : Good for path names
{
  date "+%Y/%m/%d/%H/%M/%S"
}

nanos()
{
  date +%s.%N
}

duration()
# Bash cannot do floating point arithmetic:
{
  awk -v START=${START} -v STOP=${STOP} \
      'BEGIN { printf "%.3f\n", STOP-START }' < /dev/null
}

turbine_log_kv()
# Log a formatted key/value pair:
{
  local KEY="$1" VALUE="$2"
  printf "%-19s %s\n" "$KEY" "$VALUE"
}

turbine_log()
# Backward-compatibility
{
  turbine_log_submit
}

turbine_log_submit()
# Fills in turbine.log file during job submission
# The variables referenced here must be in the environment or globals
{
  turbine_log_kv "SCRIPT:"            "${SCRIPT}"
  turbine_log_kv "JOB:"               "${JOB_ID}"
  turbine_log_kv "COMMAND:"           "${COMMAND}"
  turbine_log_kv "TURBINE_OUTPUT:"    "${TURBINE_OUTPUT}"
  turbine_log_kv "HOSTNAME:"          "$( hostname -d )"
  turbine_log_kv "SUBMITTED:"         "$( date_nice_s )"
  turbine_log_kv "PROCS:"             "${PROCS}"
  turbine_log_kv "NODES:"             "${NODES}"
  turbine_log_kv "PPN:"               "${PPN}"
  turbine_log_kv "TURBINE_WORKERS:"   "${TURBINE_WORKERS}"
  turbine_log_kv "ADLB_SERVERS:"      "${ADLB_SERVERS}"
  turbine_log_kv "WALLTIME:"          "${WALLTIME}"
  turbine_log_kv "ADLB_EXHAUST_TIME:" "${ADLB_EXHAUST_TIME}"
  turbine_log_kv "TURBINE_HOME:"      "${TURBINE_HOME}"
}

turbine_log_start()
# Fills in turbine.log file during job start
# The variables referenced here must be in the environment or globals
{
  turbine_log_kv "STARTED:" "$( date_nice_s )"
}

turbine_log_stop()
# Fills in turbine.log file after job completion
# The variables referenced here must be in the environment or globals
{
  turbine_log_kv "MPIEXEC TIME:" "${DURATION}"
  turbine_log_kv "EXIT CODE:"    "${CODE}"
  turbine_log_kv "COMPLETED:"    "$( date_nice_s )"
}
