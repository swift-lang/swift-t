#!/usr/bin/env bash
set -e

# REBUILD ALL

# Rebuilds after reconfiguration, make clean, ant clean
# Does not run ./bootstrap - provide -B if you want that

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/swift-t-settings.sh

# Override build behaviour
export FORCE_BOOTSTRAP=0
export RUN_AUTOTOOLS=1
export CONFIGURE=1
export MAKE_CLEAN=1

source ${THIS}/options.sh

source ${THIS}/internal-build-all.sh
