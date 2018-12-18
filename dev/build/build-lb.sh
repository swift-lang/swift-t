#!/usr/bin/env bash
set -eu

# BUILD LB

THIS=$(   dirname  $0 )
SCRIPT=$( basename $0 )

${THIS}/check-settings.sh
source ${THIS}/functions.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh

[[ $SKIP == *T* ]] && exit

LOG $LOG_INFO "Building lb"
cd ${LB_SRC}

run_bootstrap

EXTRA_ARGS=""

if (( SWIFT_T_DEBUG_BUILD )); then
    EXTRA_ARGS+=" --enable-log-debug"
fi

if (( SWIFT_T_TRACE_BUILD )); then
    EXTRA_ARGS+=" --enable-log-trace"
fi

if (( ENABLE_MPE )); then
    EXTRA_ARGS+=" --with-mpe=${MPE_INSTALL}"
fi

if (( DISABLE_XPT )); then
    EXTRA_ARGS+=" --enable-checkpoint=no"
fi

if (( SWIFT_T_DEV )); then
  EXTRA_ARGS+=" --enable-dev"
fi

if [[ ${MPI_VERSION} == 2 ]]; then
  EXTRA_ARGS+=" --enable-mpi-2"
fi

if (( DISABLE_ZLIB )); then
  EXTRA_ARGS+=" --without-zlib --disable-checkpoint"
fi

if [[ ${ZLIB_INSTALL:-} != "" ]]
then
  EXTRA_ARGS+=" --with-zlib=$ZLIB_INSTALL"
fi

common_args

if (( RUN_CONFIGURE )) || [[ ! -f Makefile ]]
then
  (
    rm -f config.cache
    set -eux
    ./configure --config-cache \
                --with-c-utils=${C_UTILS_INSTALL} \
                --prefix=${LB_INSTALL} \
                CC=${CC} \
                ${EXTRA_ARGS}
  )
  if (( $? ))
  then
    echo "configure failed!"
    exit 1
  fi
fi

check_make
make_clean
make_all
make_install
