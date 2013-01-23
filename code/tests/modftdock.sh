#!/bin/bash
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

set -x

THIS=$0
SCRIPT=${THIS%.sh}.tcl
OUTPUT=${THIS%.sh}.out

INPUT=test/data/input
ROOTS_TXT=${INPUT}/roots.txt
ROOTS=$( echo root{0..2} )

mkdir -p ${INPUT} || exit 1
echo ${ROOTS} | xargs -n 1 > ${ROOTS_TXT} || exit 1

for R in ${ROOTS}
do
  touch ${INPUT}/${R}.pdb
done

${SCRIPT} --list=${ROOTS_TXT} --in=input >& ${OUTPUT}
[[ ${?} == 0 ]] || exit 1

# LINES=$( grep -c "v[0-2]" ${OUTPUT} )
# [[ ${LINES} == 3 ]] || exit 1

# rm -rv ${INPUT} || exit 1

exit 0
