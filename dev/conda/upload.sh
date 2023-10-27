#!/bin/zsh
set -eu

# Get this directory
THIS=${0:A:h}
source $THIS/../../turbine/code/scripts/helpers.zsh

if (( ${#*} != 1 )) abort "upload.sh: Provide PKG!"
PKG=$1

print "uploading package: $PKG ..."
read -t 3 _ || true

/bin/time --format "upload time: %e" anaconda upload $PKG
