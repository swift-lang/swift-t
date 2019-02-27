#!/usr/bin/env zsh
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

ITERS=$1
INPUT=$2

TOTAL_START=${SECONDS}

scan -p COMMAND < ${INPUT}
COUNT=${#COMMAND}
printf "COUNT: ${#COMMAND}\n\n"

# set -x

for (( i=1 ; i<=COUNT ; i++ ))
do
  eval "DATA_${i}=()"
done

export UNIQUE=0
for (( ITER=1 ; ITER<=ITERS ; ITER++ ))
do
  print "ITER: ${ITER}"
  for (( i=1 ; i<=COUNT ; i++ ))
  do
    START=${SECONDS}
    print "${i} ${COMMAND[i]}"
    eval ${COMMAND[i]}
    STOP=${SECONDS}
    eval "DATA_${i}+=$(( STOP-START ))"
    (( UNIQUE++ ))
  done
print
done

for (( i=1 ; i<=COUNT ; i++ ))
do

  AVG=$( eval "print -l \$DATA_${i}" | avgz )
  printf "TEST: ${i}\n"
  # eval "print -l \$DATA_${i}"
  print "${AVG}"
  print
done

TOTAL_STOP=${SECONDS}
print "TOTAL_TIME: $(( TOTAL_STOP-TOTAL_START ))"

return 0
