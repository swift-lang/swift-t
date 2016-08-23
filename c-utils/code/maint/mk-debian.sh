#!/bin/zsh
set -eu

# MK DEBIAN
# Make the Debian package

echo MK DEBIAN

BINARY_PKG=""
zparseopts -D -E b:=BINARY_PKG

DEB=$1
ORIG_TGZ=$2
VERSION=$3

TOP=$PWD

BUILD_DIR=$( mktemp -d c-utils-deb-XXX )
echo $BUILD_DIR
cd $BUILD_DIR

ln ../$ORIG_TGZ
tar xfz $ORIG_TGZ
pushd exmcutils-$VERSION > /dev/null

pwd

export DEBIAN_PKG=1
(( ${#BINARY_PKG} )) && export DEBIAN_BINARY_PKG=1
debuild -us -uc

popd
mv $DEB $TOP

pwd
cd $TOP
# rm -r $BUILD_DIR
