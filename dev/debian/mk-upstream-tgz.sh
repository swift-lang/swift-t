#!/bin/sh
set -eu

# MK UPSTREAM TGZ
# For Debian package: Make the upstream TGZ

export DEBIAN_PKG_TYPE=$1
TGZ=$2
NAME=$3
VERSION=$4
FILES=$( $5 ) # A program that produces the list of files to include

if [ ${DEBIAN_PKG_TYPE} = dev ]
then
  NAME=$NAME-dev
fi

D=$( mktemp -d $NAME-deb-tgz-XXX )
mkdir $D/$NAME-$VERSION
echo CP
cp -v --parents $FILES $D/$NAME-$VERSION
echo CP OK
set -x
tar cfz $TGZ -C $D $NAME-$VERSION

echo "Created $PWD $TGZ"

rm -r $D
