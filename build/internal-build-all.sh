#!/usr/bin/env bash
set -e

THISDIR=$( cd $(dirname $0) && pwd )

if [ ! -f "${THISDIR}/exm-settings.sh" ]; then
  cp ${THISDIR}/exm-settings.sh.template ${THISDIR}/exm-settings.sh
fi

pushd ${C_UTILS_SRC}
echo
echo "Building c-utils"
pwd
echo "================"
${THISDIR}/cutils-build.sh

if [ ! -z "$ENABLE_MPE" ]; then
  cd ${THISDIR}
  export DEST=${MPE_INSTALL}
  export MPICH=${MPI_INSTALL}
  ${REPO_ROOT}/adlb_patches/make-libmpe.so.zsh
fi
popd

pushd ${LB_SRC}
echo
echo "Building lb"
pwd
echo "================"
${THISDIR}/adlb-build.sh
popd

pushd ${TURBINE_SRC}
echo
echo "Building Turbine"
pwd
echo "================"
${THISDIR}/turbine-build.sh
popd

pushd ${STC_SRC}
echo
echo "Building STC"
pwd
echo "================"
${THISDIR}/stc-build.sh
popd
