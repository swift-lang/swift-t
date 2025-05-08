#!/bin/bash
# We 'set -eu' after 'conda activate'

# SETTINGS SH
# Edit swift-t-settings.sh via settings.sed
# Assumes PWD is the top of the Swift/T clone
# We immediately cd into /dev/
# Provide -r to turn on R support

log()
{
  echo "edit-settings.sh:" ${*}
}

ACTIVATE=( source $CONDA/bin/activate base )
log ${ACTIVATE[@]}
${ACTIVATE[@]}

set -eu

cd dev
SETTINGS_SH=build/swift-t-settings.sh
SETTINGS_ORIG=build/swift-t-settings.orig

cp -v $SETTINGS_SH $SETTINGS_ORIG

log $SETTINGS_SH ...
sed -i -f github-actions/settings.sed $SETTINGS_SH

if [[ "$1" == "-r" ]]
then
  RHOME=$( R RHOME )
  log "RHOME=$RHOME"
  sed -i 's/ENABLE_R=0/ENABLE_R=1/'       $SETTINGS_SH
  sed -i "s@# R_INSTALL=.*@R_INSTALL=$RHOME@" $SETTINGS_SH
fi

log "settings changed:"
diff $SETTINGS_ORIG $SETTINGS_SH
