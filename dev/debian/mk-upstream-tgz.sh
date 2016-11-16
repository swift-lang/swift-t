#!/bin/sh
set -eu

# MK UPSTREAM TGZ
# For Debian package: Make the upstream TGZ
# Used internally by Makefiles

echo "Building upstream TGZ..."

if [ ${#} != 5 ]
then
  echo "mk-upstream-tgz: usage: DEBIAN_PKG_TYPE TGZ NAME VERSION FILE_LIST"
  exit 1
fi

DEBIAN_PKG_TYPE=$1 # Package type: dev or bin
TGZ=$2             # Output TGZ file
NAME=$3            # TGZ name
VERSION=$4         # TGZ version
FILE_LIST=$5       # Program that produces list of files to include

export DEBIAN_PKG_TYPE  # Export this to FILE_LIST program
FILES=$( $FILE_LIST )

if [ $DEBIAN_PKG_TYPE = dev ]
then
  NAME=$NAME-dev
fi

echo NAME: $NAME

D=$( mktemp -d .$NAME-deb-tgz-XXX )
mkdir -v $D/$NAME-$VERSION
cp -v --parents $FILES $D/$NAME-$VERSION

tar cfz $TGZ -C $D $NAME-$VERSION

echo "Created $PWD $TGZ"
rm -r $D
