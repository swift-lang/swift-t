#!/usr/bin/env bash
set -e

THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh

if (( MAKE_CLEAN )); then
  if [ -f Makefile ]; then
    # Disabled due to Turbine configure check
    #make clean
    :
  fi
fi

if (( SVN_UPDATE )); then
  svn update
fi

EXTRA_ARGS=
if (( EXM_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
fi

if (( RUN_AUTOTOOLS )); then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
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

if (( EXM_CRAY )); then
  if (( EXM_STATIC_BUILD )); then
    export CC=cc
  else
    export CC=gcc
  fi
  export CFLAGS="-g -O2"
fi

if (( CONFIGURE )); then
  ./configure --enable-shared --prefix=${C_UTILS_INSTALL} ${EXTRA_ARGS}
fi
make -j ${MAKE_PARALLELISM}
make install
