#!/usr/bin/env bash
set -e

THISDIR=$( cd $(dirname $0) && pwd )

cd ${C_UTILS}
echo
echo "Building c-utils"
pwd
echo "================"
${THISDIR}/cutil_build.sh

if [ ! -z "$ENABLE_MPE" ]; then
  cd ${THISDIR}
  export DEST=${MPE_INST}
  export MPICH=${MPICH_INST}
  ${REPO_ROOT}/adlb_patches/make-libmpe.so.zsh
fi

cd ${LB}
echo
echo "Building lb"
pwd
echo "================"
${THISDIR}/adlb_build.sh

cd ${TURBINE}
echo
echo "Building Turbine"
pwd
echo "================"
${THISDIR}/turbine-build.sh

cd ${STC}
echo
echo "Building STC"
pwd
echo "================"
if (( SVN_UPDATE )); then
  svn update
fi
if (( MAKE_CLEAN )); then
  ant clean
fi
ant ${STC_ANT_ARGS}
