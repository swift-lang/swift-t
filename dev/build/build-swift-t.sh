#!/usr/bin/env bash
set -eu

# BUILD SWIFT-T

# Main user interface
# Swift/T build script: runs configuration and compilation
# See options.sh for options

THIS=$( cd $( dirname $0 ) ; /bin/pwd )
SCRIPT=$( basename $0 )

cd $THIS

$THIS/check-settings.sh
source $THIS/functions.sh
source $THIS/options.sh
source $THIS/swift-t-settings.sh
$THIS/check-tools.sh
source $THIS/setup.sh

LOG $LOG_WARN "Installing Swift/T into: $SWIFT_T_PREFIX"
if (( RUN_CONFIGURE ))
then
  LOG_WAIT 3
fi

check_lock $SWIFT_T_PREFIX

LOG $LOG_INFO ""
$THIS/build-cutils.sh

LOG $LOG_INFO ""
$THIS/build-lb.sh

LOG $LOG_INFO ""
$THIS/build-turbine.sh

LOG $LOG_INFO ""
$THIS/build-stc.sh

LOG $LOG_INFO
LOG $LOG_WARN "Swift/T build successful."

cp $THIS/lock.sh $SWIFT_T_PREFIX
