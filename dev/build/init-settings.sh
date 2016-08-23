#!/usr/bin/env bash
set -eu

# INIT SETTINGS

THIS=$( cd $(dirname $0) && pwd )

if [ ! -f ${THIS}/swift-t-settings.sh ]
then
  cp ${THIS}/swift-t-settings.sh.template ${THIS}/swift-t-settings.sh
  echo "Created ${THIS}/swift-t-settings.sh"
  echo "You may edit swift-t-settings.sh before building."
else
  echo "Already exists: ${THIS}/swift-t-settings.sh"
fi
