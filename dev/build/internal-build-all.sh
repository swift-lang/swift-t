#!/usr/bin/env bash
set -e

# INTERNAL BUILD ALL

THIS=$( cd $(dirname $0) && /bin/pwd )

${THIS}/check-tools.sh

echo
${THIS}/build-cutils.sh

echo
${THIS}/build-lb.sh

echo
${THIS}/build-turbine.sh

echo
${THIS}/build-stc.sh
