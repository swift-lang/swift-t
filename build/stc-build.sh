#!/usr/bin/env bash
set -e
THISDIR=`dirname $0`
source ${THISDIR}/exm-settings.sh

if (( SVN_UPDATE )); then
  svn update
fi
if (( MAKE_CLEAN )); then
  ${ANT} clean
fi
${ANT} ${STC_ANT_ARGS}

if [ ! -z "${STC_INSTALL}" ]
then
  ${ANT} -Ddist.dir="${STC_INSTALL}" -Dturbine.home="${TURBINE_INSTALL}" install
fi
