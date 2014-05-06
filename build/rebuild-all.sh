#!/usr/bin/env bash
set -e
THISDIR=`dirname $0`

source "${THISDIR}/init-settings.sh"

# Override build behaviour
export RUN_AUTOTOOLS=1
export CONFIGURE=1
export MAKE_CLEAN=1

source ${THISDIR}/internal-build-all.sh
