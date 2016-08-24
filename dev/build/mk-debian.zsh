#!/bin/sh
set -eu

# MK DEBIAN
# Make the Debian package

echo "MK DEBIAN"

DEBIAN_PKG_TYPE=$1
DEB=$2
ORIG_TGZ=$3
NAME=$4
VERSION=$5

TOP=$PWD

BUILD_DIR=$( mktemp -d deb-work-XXX )
echo $BUILD_DIR
cd $BUILD_DIR

export DEBIAN_PKG=1
if [ ${DEBIAN_PKG_TYPE} = "bin" ]
then
  export DEBIAN_BINARY_PKG=1
else
  NAME=$NAME-dev
fi

ln ../$ORIG_TGZ
tar xfz $ORIG_TGZ
(
  cd $NAME-$VERSION
  pwd
  debuild -us -uc
)

mv $DEB $TOP

pwd
cd $TOP
# rm -r $BUILD_DIR
