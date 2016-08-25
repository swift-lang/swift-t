#!/usr/bin/env bash
set -e

# BUILD ALL

# Swift/T build script: runs configuration and compilation

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/swift-t-settings.sh
source ${THIS}/internal-build-all.sh
