#!/bin/bash

grep -q "trace: 81,81" ${TURBINE_OUTFILE} || exit 1

exit 0
