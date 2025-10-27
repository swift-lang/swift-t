#!/usr/bin/env bash
set -eu

# INIT SETTINGS

THIS=$( cd $(dirname $0) && pwd )

if [[ -f ${THIS}/swift-t-settings.sh ]]
then
  echo "Already exists: ${THIS}/swift-t-settings.sh"
  echo "Move or delete this file and try again."
  exit 1
fi

cp ${THIS}/swift-t-settings.sh.template ${THIS}/swift-t-settings.sh

if (( ${CONDA_BUILD:-0} == 0 ))
then
  # This output is suppressed during a conda build
  echo "Created ${THIS}/swift-t-settings.sh"
  echo "You may edit swift-t-settings.sh before building."
fi
