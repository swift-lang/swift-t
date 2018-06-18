#!/usr/bin/env bash
set -eu

# BUILD C-UTILS

THIS=$( dirname $0 )
source ${THIS}/swift-t-settings.sh

EXTRA_ARGS=""
if (( SWIFT_T_OPT_BUILD )); then
    EXTRA_ARGS+="--enable-fast"
fi

if (( RUN_BOOTSTRAP )); then
  rm -rfv config.cache config.status autom4te.cache
  ./bootstrap
elif [ ! -f configure ] ; then
  ./bootstrap
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

if (( ! RUN_MAKE ))
then
  exit
fi

if (( MAKE_CLEAN ))
then
  if [ -f Makefile ]
  then
    make clean
  fi
fi

make -j ${MAKE_PARALLELISM}
make install
