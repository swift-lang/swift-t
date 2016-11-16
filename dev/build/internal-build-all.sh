#!/usr/bin/env bash
set -e

THIS=$( cd $(dirname $0) && pwd )

${THIS}/check-tools.sh

pushd ${C_UTILS_SRC} > /dev/null
echo
echo "Building c-utils in $PWD"
${THIS}/cutils-build.sh
popd > /dev/null

pushd ${LB_SRC} > /dev/null
echo
echo "Building lb in $PWD"
${THIS}/lb-build.sh
popd > /dev/null

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

pushd ${TURBINE_SRC} > /dev/null
echo
echo "Building Turbine in $PWD"
${THIS}/turbine-build.sh
popd > /dev/null

pushd ${STC_SRC} > /dev/null
echo
echo "Building STC in $PWD"
${THIS}/stc-build.sh
popd > /dev/null
