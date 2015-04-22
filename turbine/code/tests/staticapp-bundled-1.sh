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

source tests/test-helpers.sh

THIS=$0
BIN=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out
TESTS=$( dirname $0 )
source ${TESTS}/setup.sh > ${OUTPUT} 2>&1

set -x
export TURBINE_USER_LIB=${THIS%.sh}/

export MKSTATIC_TMPDIR=$(mktemp -d)

echo "MKSTATIC_TMPDIR=${MKSTATIC_TMPDIR}"

${TESTS}/run-mpi.zsh ${BIN} >& ${OUTPUT}
[[ ${?} == 0 ]] || test_result 1

for f in ${THIS%.sh}.*.data
do
  dst="${MKSTATIC_TMPDIR}/$(basename $f)"
  if [ ! -f "$dst" ]
  then
    echo "Expected data file to be extracted at $dst"
    exit 1
  fi

  if ! diff -q $f $dst
  then
    echo "Data file $f and extracted $dst differ"
    exit 1
  fi  
done

rm -rf "${MKSTATIC_TMPDIR}"

test_result 0
