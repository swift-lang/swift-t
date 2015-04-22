#!/bin/bash
set -e

THIS=$0
EXEC=$(dirname ${THIS})/workq_bench.x
OUTPUT=${THIS%.sh}.out

${EXEC} > ${OUTPUT} 2>&1 
