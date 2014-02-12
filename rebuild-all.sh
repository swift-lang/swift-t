#!/usr/bin/env bash
set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/exm-settings.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}

# Override build behaviour
export RUN_AUTOTOOLS=1
export CONFIGURE=1
export MAKE_CLEAN=1

source ${THISDIR}/internal-build-all.sh
