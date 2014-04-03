#!/bin/zsh

set -ux

pwd
TESTS_SKIP=0
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
export PATH=${MPICH}/bin:${TURBINE}/bin:${STC}/bin:${PATH}
print -l ${path}
which stc
which turbine
turbine -v
wget -O STC_results_aggregator.sh  http://dl.dropbox.com/u/1739272/STC_results_aggregator.sh
chmod a+x *sh

###############################This runs the tests###############################
./run-tests.zsh -c -k $TESTS_SKIP -S $PATH 2>&1 | tee RESULTS_FILE
#./run-tests.zsh -c -k $TESTS_SKIP 2>&1 | tee RESULTS_FILE
ls -thor
#######Script to create the xml output###########################################
./STC_results_aggregator.sh
#perl -CSDA -pe'
#   s/[^\x9\xA\xD\x20-\x{D7FF}\x{E000}-\x{FFFD}\x{10000}-\x{10FFFF}]+//g;
#' result_aggregate.xml > result_aggregate.xml
