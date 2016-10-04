#!/usr/bin/env bash
set -e

THIS=$( cd $(dirname $0) && pwd )

${THIS}/check-tools.sh

pushd ${C_UTILS_SRC}
echo
echo "Building c-utils"
pwd
echo "================"
${THIS}/cutils-build.sh
popd

pushd ${LB_SRC}
echo
echo "Building lb"
pwd
echo "================"
${THIS}/lb-build.sh
popd

if [ ! -z "$COASTER_SRC" -a ! -z "$COASTER_INSTALL" ]
then
  pushd ${COASTER_SRC}
  echo
  echo "Building Coaster C Client"
  pwd
  echo "========================="
  ${THIS}/coaster-c-client-build.sh
  popd
fi

pushd ${TURBINE_SRC}
echo
echo "Building Turbine"
pwd
echo "================"
${THIS}/turbine-build.sh
popd

pushd ${STC_SRC}
echo
echo "Building STC"
pwd
echo "================"
${THIS}/stc-build.sh
popd
