#!/usr/bin/env bash
# Fast build script that avoids rebuilding.  May not work if configure
# files have changed

set -e
THISDIR=`dirname $0`

source "${THISDIR}/init-settings.sh"

# Override build behaviour
export RUN_AUTOTOOLS=0
export CONFIGURE=0
export MAKE_CLEAN=0
export SVN_UPDATE=0

source ${THISDIR}/internal-build-all.sh
