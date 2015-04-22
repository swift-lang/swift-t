#!/bin/sh -ex

grep -q "MY USER ERROR MESSAGE" ${TURBINE_OUTPUT} || exit 1

exit 0
