#!/usr/bin/env bash
set -e

# FAST BUILD ALL

# Fast build script that avoids reconfiguring.
# May not work if configure files have changed.

THIS=$( dirname $0 )

# Override build behaviour
export RUN_AUTOTOOLS=0
export CONFIGURE=0
export MAKE_CLEAN=0

${THIS}/check-settings.sh
source ${THIS}/swift-t-settings.sh
source ${THIS}/internal-build-all.sh
