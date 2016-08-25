#!/bin/sh
set -eu

# MK DEBIAN
# Make the Debian package

echo "MK DEBIAN"

if [ ${#} != 5 ]
then
  echo "mk-debian: usage: DEBIAN_PKG_TYPE DEB ORIG_TGZ NAME VERSION"
  exit 1
fi

DEBIAN_PKG_TYPE=$1 # Package type: dev or bin
DEB=$2             # Output DEB file
ORIG_TGZ=$3        # Upstream TGZ file
NAME=$4            # Debian name
VERSION=$5         # Debian version

TOP=$PWD

BUILD_DIR=$( mktemp -d deb-work-XXX )
echo "Working in: $BUILD_DIR"
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
  debuild -eDEB_BUILD_OPTIONS="parallel=4" -us -uc
)

mv -v $DEB $TOP

cd $TOP
# rm -r $BUILD_DIR
