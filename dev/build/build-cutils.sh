#!/usr/bin/env bash
set -eu

# BUILD C-UTILS

THIS=$(   dirname  $0 )
SCRIPT=$( basename $0 )

${THIS}/check-settings.sh
source ${THIS}/functions.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh
source ${THIS}/setup.sh

[[ $SKIP == *T* ]] && exit

LOG $LOG_INFO "Building c-utils"
cd ${C_UTILS_SRC}

run_bootstrap

EXTRA_ARGS=""

common_args

if (( RUN_CONFIGURE )) || [[ ! -f Makefile ]]
then
  rm -f config.cache
  (
    set -eux
    ${NICE_CMD} ./configure --config-cache \
                --prefix=${C_UTILS_INSTALL} \
                --enable-shared \
                ${EXTRA_ARGS} \
                ${CUSTOM_CFG_ARGS_C_UTILS:-}
  )
fi

check_make
make_clean
make_all
make_install
