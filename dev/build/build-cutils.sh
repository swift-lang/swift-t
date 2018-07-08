#!/usr/bin/env bash
set -eu

# BUILD C-UTILS

THIS=$( dirname $0 )
${THIS}/check-settings.sh
source ${THIS}/functions.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh

[[ $SKIP == *T* ]] && exit

LOG $LOG_INFO "Building c-utils"
cd ${C_UTILS_SRC}

run_bootstrap

EXTRA_ARGS=""
if (( SWIFT_T_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
fi

if (( SWIFT_T_DEBUG_BUILD )); then
   export CFLAGS="-g -O0"
fi

if (( DISABLE_SHARED )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
fi

if (( RUN_CONFIGURE )) || [[ ! -f Makefile ]]
then
  rm -f config.cache
  (
    set -eux
    ./configure --config-cache \
                --prefix=${C_UTILS_INSTALL} \
                --enable-shared \
                ${EXTRA_ARGS}
  )
fi

check_make
make_clean
make_all
make_install
