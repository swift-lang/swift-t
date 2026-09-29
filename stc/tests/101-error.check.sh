#!/bin/sh -ex

grep -q "MY USER ERROR MESSAGE" ${TURBINE_OUTFILE} || exit 1

exit 0
