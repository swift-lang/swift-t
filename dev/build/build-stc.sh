#!/usr/bin/env bash
set -eu

# BUILD STC

THIS=$(   readlink --canonicalize $( dirname  $0 ) )
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

if (( RUN_MAKE_CLEAN ))
then
  $ANT clean
fi

if (( ! RUN_MAKE ))
then
  exit
fi

# The main Ant build step
$NICE_CMD $ANT $STC_ANT_ARGS

if (( ! RUN_MAKE_INSTALL ))
then
  exit
fi

if (( ${#STC_INSTALL} > 0 ))
then
  $NICE_CMD $ANT -Ddist.dir="$STC_INSTALL" \
                 -Dturbine.home="$TURBINE_INSTALL" \
                 -Duse.java="$USE_JAVA" \
                 install
fi
