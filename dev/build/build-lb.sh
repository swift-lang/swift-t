#!/usr/bin/env bash
set -eu

# BUILD LB

THISDIR=$( dirname $0 )
source ${THISDIR}/swift-t-settings.sh

if (( RUN_BOOTSTRAP )) || [ ! -f configure ]; then
  ./bootstrap
fi

EXTRA_ARGS=""
if (( SWIFT_T_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast "
fi

if (( SWIFT_T_DEBUG_BUILD )); then
    EXTRA_ARGS+="--enable-log-debug "
fi

if (( SWIFT_T_TRACE_BUILD )); then
    EXTRA_ARGS+="--enable-log-trace "
fi

if (( ENABLE_MPE )); then
    EXTRA_ARGS+="--with-mpe=${MPE_INSTALL} "
fi

if (( DISABLE_SHARED )); then
  EXTRA_ARGS+=" --disable-shared"
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

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
fi

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
fi

if (( ! RUN_MAKE ))
then
  exit
fi

if (( MAKE_CLEAN ))
then
  rm -fv config.cache
  if [ -f Makefile ]
  then
    make clean
  fi
fi

make -j ${MAKE_PARALLELISM}

if (( ! RUN_MAKE_INSTALL ))
then
  exit
fi
make install
