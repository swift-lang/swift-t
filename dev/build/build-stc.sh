#!/usr/bin/env bash
set -eu

# BUILD STC

THIS=$(   cd $( dirname $0 ) ; /bin/pwd )
SCRIPT=$( basename $0 )

cd $THIS

$THIS/check-settings.sh
source $THIS/functions.sh
source $THIS/options.sh
source $THIS/swift-t-settings.sh
source $THIS/setup.sh

[[ $SKIP == *S* ]] && exit

echo "Building STC in $PWD"
cd $STC_SRC

check_lock $SWIFT_T_PREFIX/stc

echo "Ant and Java settings:"
which $ANT java

echo "JAVA_HOME: '${JAVA_HOME:-}'"
echo "ANT_HOME:  '${ANT_HOME:-}'"
echo

USE_JAVA=$( which java )
: ${CONDA_BUILD:=0}
ENABLE_CONDA=""

if (( $CONDA_BUILD ))
then
  USE_JAVA=$JAVA_HOME/bin/java
  ENABLE_CONDA="-Dconda="
  echo "Detected CONDA_BUILD: USE_JAVA=$USE_JAVA"
fi

if (( RUN_MAKE_CLEAN ))
then
  $ANT clean
fi

if (( ! RUN_MAKE ))
then
  exit
fi

# The main Ant build step
(
  set -x
  $NICE_CMD $ANT $STC_ANT_ARGS
)

if (( ! RUN_MAKE_INSTALL ))
then
  exit
fi

TIMESTAMP="$( date "+%Y-%m-%d %H:%M:%S" )"
if (( ${#STC_INSTALL} > 0 ))
then
  set -x
  $NICE_CMD $ANT -Ddist.dir="$STC_INSTALL"         \
                 -Dturbine.home="$TURBINE_INSTALL" \
                 -Duse.java="$USE_JAVA"            \
                 -Dtimestamp="$TIMESTAMP"          \
                 $ENABLE_CONDA                     \
                 install
fi
