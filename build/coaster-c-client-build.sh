#!/usr/bin/env bash
# Run this script from inside coaster c client source directory
set -e
THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh

if [ -z "${COASTER_INSTALL}" ]; then
  echo "No coaster C client install directory specified"
  exit 1
fi

EXTRA_ARGS=""

if (( DISABLE_STATIC )); then
  EXTRA_ARGS+=" --disable-static"
else
  EXTRA_ARGS+=" --enable-static"
fi

if (( EXM_STATIC_BUILD )); then
  EXTRA_ARGS+=" --disable-shared"
fi

if (( EXM_DEBUG_BUILD )); then
  EXTRA_ARGS+=" --enable-debug"
fi

if (( EXM_OPT_BUILD )); then
  : # Not available yet
fi

if [ -f Makefile ]; then
  make distclean
fi

./autogen.sh

./configure --prefix=${COASTER_INSTALL} ${EXTRA_ARGS}
make -j ${MAKE_PARALLELISM}
make install
