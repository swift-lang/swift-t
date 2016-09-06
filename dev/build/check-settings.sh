#!/usr/bin/env bash
set -eu

# CHECK SETTINGS

THIS=$( cd $(dirname $0) && pwd )

if [ ! -f ${THIS}/swift-t-settings.sh ]
then
  echo "Could not find swift-t-settings.sh"
  echo "Use init-settings.sh or see the manual."
  exit 1
fi

exit
