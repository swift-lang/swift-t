#!/usr/bin/env bash
set -e
set -x

THISDIR=`dirname $0`
source ${THISDIR}/build-vars.sh

export CC=mpicc
if (( MAKE_CLEAN )); then
  if [ -f Makefile ]; then
    make clean
  fi
fi

if (( SVN_UPDATE )); then
  svn update
fi

if (( SKIP_AUTOTOOLS )); then
  rm -rf ./config.status ./autom4te.cache
  ./setup.sh
fi

EXTRA_ARGS=
if (( EXM_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast "
fi

if (( EXM_DEBUG_BUILD )); then
    EXTRA_ARGS+="--enable-log-debug "
fi

if (( EXM_TRACE_BUILD )); then
    EXTRA_ARGS+="--enable-log-trace "
fi

if (( ENABLE_MPE )); then
    EXTRA_ARGS+="--with-mpe=${MPE_INST} "
fi

if (( EXM_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( EXM_CRAY )); then
  if (( EXM_STATIC_BUILD )); then
    export CC="cc"
    export CFLAGS="-g -O2"
  else
    export CC="gcc"
    export CFLAGS="-g -O2 -I${MPICH_INST}/include"
    export LDFLAGS="-L${MPICH_INST}/lib -lmpich"
  fi
  EXTRA_ARGS+=" --enable-mpi-2"
fi

if (( DISABLE_XPT )); then
    EXTRA_ARGS+=" --enable-checkpoint=no"
fi

if [[ ! -z "$EXM_DEV" && "$EXM_DEV" != 0 ]]; then
  EXTRA_ARGS+=" --enable-dev"
fi

if (( CONFIGURE )); then
  ./configure --with-c-utils=${C_UTILS_INST} \
              --prefix=${LB_INST} ${EXTRA_ARGS}
fi
if (( MAKE_CLEAN )); then
  make clean
fi
make -j ${MAKE_PARALLELISM}
make install
