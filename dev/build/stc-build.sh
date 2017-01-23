#!/usr/bin/env bash
set -e

# STC BUILD

THISDIR=$( dirname $0 )
source ${THISDIR}/swift-t-settings.sh

echo "Java build settings:"
which ant java
echo $JAVA_HOME
echo $ANT_HOME
echo

if (( MAKE_CLEAN )); then
  ${ANT} clean
fi

if (( ! RUN_MAKE )); then
  exit
fi

${ANT} ${STC_ANT_ARGS}

if [ ! -z "${STC_INSTALL}" ]
then
  ${ANT} -Ddist.dir="${STC_INSTALL}" -Dturbine.home="${TURBINE_INSTALL}" install
fi
