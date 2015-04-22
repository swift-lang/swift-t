#!/bin/bash
set -e

THIS=$0
EXEC=${THIS%.sh}.x
OUTPUT=${THIS%.sh}.out

${EXEC} > ${OUTPUT} 2>&1 
