#!/usr/bin/env bash
set -e

# INTERNAL BUILD ALL

THIS=$( cd $(dirname $0) && /bin/pwd )

${THIS}/check-tools.sh

echo
echo "Building c-utils"
${THIS}/build-cutils.sh

echo
echo "Building lb"
${THIS}/build-lb.sh

echo
echo "Building Turbine"
${THIS}/build-turbine.sh

echo
echo "Building STC in $PWD"
${THIS}/build-stc.sh
