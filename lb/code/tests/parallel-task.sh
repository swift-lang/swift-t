#!/bin/bash
set -ex

THIS=$0
EXEC=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

mpiexec -n 4 ${EXEC} > ${OUTPUT} 2>&1
