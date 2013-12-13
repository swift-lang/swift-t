#!/usr/bin/env bash
set -x
set -e
THISDIR=`dirname $0`

BUILDVARS=${THISDIR}/build-vars.sh
if [ ! -f ${BUILDVARS} ] ; then
  echo "Need ${BUILDVARS} to exist"
  exit 1
fi
source ${BUILDVARS}

cd ${C_UTILS}
${DEV_DIR}/cutil_build.sh

if [ ! -z "$ENABLE_MPE" ]; then
  cd ${DEV_DIR}
  export DEST=${MPE_INST}
  export MPICH=${MPICH_INST}
  ${REPO_ROOT}/adlb_patches/make-libmpe.so.zsh
fi

cd ${LB}
${DEV_DIR}/adlb_build.sh

cd ${TURBINE}
${DEV_DIR}/turbine-build.sh

cd ${STC}
if (( SVN_UPDATE )); then
  svn update
fi
if (( MAKE_CLEAN )); then
  ant clean
fi
ant ${STC_ANT_ARGS}
