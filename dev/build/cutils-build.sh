#!/usr/bin/env bash
set -e

THIS=$( dirname $0 )
source ${THIS}/swift-t-settings.sh

if (( MAKE_CLEAN )); then
  if [ -f Makefile ]; then
    make clean
  fi
fi

EXTRA_ARGS=
if (( EXM_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
fi

if (( RUN_AUTOTOOLS )); then
  rm -rf ./config.status ./autom4te.cache
  ./bootstrap
elif [ ! -f configure ]; then
  # Attempt to run autotools
  ./bootstrap
fi

if (( EXM_DEBUG_BUILD )); then
   export CFLAGS="-g -O0"
fi

if (( EXM_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
fi

set -x
if (( CONFIGURE )); then
  ./configure --enable-shared --prefix=${C_UTILS_INSTALL} ${EXTRA_ARGS}
fi
make -j ${MAKE_PARALLELISM}
make install
