#!/bin/sh
set -eu

# SETTINGS SH
# Edit swift-t-settings.sh via settings.sed
# Assumes PWD is the top of the Swift/T clone
# We immediately cd into /dev/

cd dev
SETTINGS_SH=build/swift-t-settings.sh
echo "Editing $SETTINGS_SH ..."
sed -i -f github-actions/settings.sed build/swift-t-settings.sh
