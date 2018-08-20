#!/usr/bin/env bash
set -eu

# BUILD STC

THIS=$( dirname $0 )
${THIS}/check-settings.sh
source ${THIS}/functions.sh
source ${THIS}/options.sh
source ${THIS}/swift-t-settings.sh

[[ $SKIP == *S* ]] && exit

echo "Building STC in $PWD"
cd ${STC_SRC}

echo "Ant and Java settings:"
which $ANT java

echo "JAVA_HOME: '${JAVA_HOME:-}'"
echo "ANT_HOME:  '${ANT_HOME:-}'"
echo

if (( RUN_MAKE_CLEAN )); then
  ${ANT} clean
fi

if (( ! RUN_MAKE )); then
  exit
fi

${ANT} ${STC_ANT_ARGS}

if (( ! RUN_MAKE_INSTALL )); then
  exit
fi

if [ ! -z "${STC_INSTALL}" ]
then
  ${ANT} -Ddist.dir="${STC_INSTALL}" \
         -Dturbine.home="${TURBINE_INSTALL}" \
         install
fi
