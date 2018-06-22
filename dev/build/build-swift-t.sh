#!/usr/bin/env bash
set -eu

# BUILD SWIFT-T

# Main user interface
# Swift/T build script: runs configuration and compilation
# See options.sh for options

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh
source ${THIS}/internal-build-all.sh

echo
echo "Swift/T build successful."
