#!/bin/bash
set -eu

# SETTINGS SH
# Edit swift-t-settings.sh via settings.sed
# Assumes PWD is the top of the Swift/T clone
# We immediately cd into /dev/
# Provide -r to turn on R support

cd dev
SETTINGS_SH=build/swift-t-settings.sh
SETTINGS_ORIG=build/swift-t-settings.orig

cp -v $SETTINGS_SH $SETTINGS_ORIG

echo "Editing $SETTINGS_SH ..."
sed -i -f github-actions/settings.sed $SETTINGS_SH

if [[ "$1" == "-r" ]]
then
  RHOME=$( R RHOME )
  echo "RHOME=$RHOME"
  sed -i 's/ENABLE_R=0/ENABLE_R=1/'       $SETTINGS_SH
  sed -i "s@# R_INSTALL=.*@R_INSTALL=$RHOME@" $SETTINGS_SH
fi

echo "Settings changed:"
diff $SETTINGS_ORIG $SETTINGS_SH
