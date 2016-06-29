#!/usr/bin/env bash

THIS=$( cd $(dirname $0) && pwd )

if [ ! -f "${THIS}/swift-t-settings.sh" ]; then
  cp "${THIS}/swift-t-settings.sh.template" "${THIS}/swift-t-settings.sh"
  echo "Created ${THIS}/swift-t-settings.sh"
else
  echo "Already exists: ${THIS}/swift-t-settings.sh"
fi

source "${THIS}/swift-t-settings.sh"
echo "Sourced ${THIS}/swift-t-settings.sh"
echo "Installation target is: SWIFT_T_PREFIX=${SWIFT_T_PREFIX}"
echo "You may edit swift-t-settings.sh and source again before building."
