#!/usr/bin/env bash
set -eu

# BUILD SWIFT-T

# Main user interface
# Swift/T build script: runs configuration and compilation
# See options.sh for options

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/options.sh
source ${THIS}/functions.sh
source ${THIS}/swift-t-settings.sh
source ${THIS}/internal-build-all.sh

LOG $LOG_INFO
LOG $LOG_INFO "Swift/T build successful."
