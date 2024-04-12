#!/bin/sh
set -eu

# CHECK SETTINGS

# Fail nicely if the user did not create a settings file (via init-settings)

THIS=$( cd $(dirname $0) && pwd )

if [ ! -f ${THIS}/swift-t-settings.sh ]
then
  echo "Could not find swift-t-settings.sh"
  echo "Use init-settings.sh or see the manual."
  exit 1
fi
