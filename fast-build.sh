#!/usr/bin/env bash
# Fast build script that avoids rebuilding.  May not work if configure
# files have changed

set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/build-vars.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}

# Override build behaviour
export RUN_AUTOTOOLS=0
export CONFIGURE=0
export MAKE_CLEAN=0
export SVN_UPDATE=0

source ${THISDIR}/build-all.sh
