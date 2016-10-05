#!/usr/bin/env bash
set -e

# REBUILD ALL

# Rebuilds after reconfiguration, make clean, ant clean

THIS=$( dirname $0 )

${THIS}/check-settings.sh
source ${THIS}/swift-t-settings.sh

# Override build behaviour
export RUN_AUTOTOOLS=1
export CONFIGURE=1
export MAKE_CLEAN=1

source ${THIS}/options.sh

source ${THIS}/internal-build-all.sh
