#!/usr/bin/env bash
set -e

THISDIR=$( dirname $0 )
source ${THISDIR}/swift-t-settings.sh

if (( MAKE_CLEAN )); then
  if [ -f Makefile ]; then
    make clean
  fi
fi

if (( RUN_AUTOTOOLS )); then
  ./bootstrap
elif [ ! -f configure ]; then
  # Attempt to run autotools
  ./bootstrap
fi

EXTRA_ARGS=
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

if (( SWIFT_T_STATIC_BUILD )); then
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

if [ ! -z "$ZLIB_INSTALL" ]; then
  EXTRA_ARGS+=" --with-zlib=$ZLIB_INSTALL"
fi

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
fi

set -x
if (( CONFIGURE )); then
  ./configure --config-cache \
              --with-c-utils=${C_UTILS_INSTALL} \
              --prefix=${LB_INSTALL} \
              ${EXTRA_ARGS}
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
make install
