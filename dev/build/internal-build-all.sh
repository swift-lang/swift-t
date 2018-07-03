#!/usr/bin/env bash
set -e

# INTERNAL BUILD ALL

THIS=$( cd $(dirname $0) && /bin/pwd )

${THIS}/check-tools.sh
source ${THIS}/functions.sh

LOG $LOG_INFO ""
${THIS}/build-cutils.sh

LOG $LOG_INFO ""
${THIS}/build-lb.sh

LOG $LOG_INFO ""
${THIS}/build-turbine.sh

LOG $LOG_INFO ""
${THIS}/build-stc.sh
