#!/bin/sh
set -eu

# MK UPSTREAM TGZ
# For Debian package: Make the upstream TGZ

TGZ=$1
NAME=$2
VERSION=$3
FILES=$( $4 ) # A program that produces the list of files to include

D=$( mktemp -d $NAME-deb-tgz-XXX )
mkdir $D/$NAME-$VERSION
cp -v --parents $FILES $D/$NAME-$VERSION
tar cfz $TGZ -C $D $NAME-$VERSION

echo "Created $PWD $TGZ"

rm -r $D
