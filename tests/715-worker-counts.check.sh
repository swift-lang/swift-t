#!/usr/bin/env bash

# Take account of the fact that tests can be run with varying process
# counts.  Make use of fact that these are specified by environment
# variables.  Assume all default to one

check_count workers ${TEST_ADLB_WORKERS:-1}
check_count servers ${TEST_ADLB_SERVERS:-1}
check_count engines ${TEST_TURBINE_ENGINES:-1}

function check_count() {
  local TYPE=$1
  local EXP=$2
  echo "Expected ${TYPE}:${EXP}"
  if grep -F -q "$TYPE,$EXP"
  then
    echo "FOUND!"
  else
    echo "Wrong count!"
    exit 1
  fi
}
