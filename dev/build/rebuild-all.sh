#!/usr/bin/env bash
set -e

# REBUILD ALL

# Rebuilds after reconfiguration, make clean, ant clean

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/swift-t-settings.sh

# Override build behaviour
export FORCE_BOOTSTRAP=0
export RUN_AUTOTOOLS=1
export CONFIGURE=1
export MAKE_CLEAN=1

source ${THIS}/options.sh

echo $FORCE_BOOTSTRAP

source ${THIS}/internal-build-all.sh
