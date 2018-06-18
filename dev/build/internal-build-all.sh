#!/usr/bin/env bash
set -e

# INTERNAL BUILD ALL

THIS=$( cd $(dirname $0) && pwd )

${THIS}/check-tools.sh

pushd ${C_UTILS_SRC} > /dev/null
echo
echo "Building c-utils in $PWD"
${THIS}/build-cutils.sh
popd > /dev/null

pushd ${LB_SRC} > /dev/null
echo
echo "Building lb in $PWD"
${THIS}/build-lb.sh
popd > /dev/null

pushd ${TURBINE_SRC} > /dev/null
echo
echo "Building Turbine in $PWD"
${THIS}/build-turbine.sh
popd > /dev/null

pushd ${STC_SRC} > /dev/null
echo
echo "Building STC in $PWD"
${THIS}/build-stc.sh
popd > /dev/null
