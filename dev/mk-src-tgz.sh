#!/bin/sh
set -eu

# MK SRC TGZ
# For Debian package or Spack: Make the upstream or source TGZ
# Used internally by Makefiles for Debian
# Used by dev/build-spacks.sh
# This is used for one individual module at a time:
#      ExM c-utils, ADLB/X, Turbine, or STC.

if [ ${#} != 5 ]
then
  echo "mk-src-tgz: usage: PKG_TYPE TGZ NAME VERSION FILE_LIST"
  echo "mk-src-tgz: given: $*"
  exit 1
fi

PKG_TYPE=$1  # Package type: src or deb-dev or deb-bin or spack
TGZ=$2       # Output TGZ file
NAME=$3      # TGZ name
VERSION=$4   # TGZ version
FILE_LIST=$5 # Program that produces list of files to include

echo "Building upstream TGZ for $NAME ..."

# Export PKG_TYPE and DEB_TYPE to FILE_LIST program
export PKG_TYPE=$PKG_TYPE
case $PKG_TYPE in
  deb-bin) DEB_TYPE=bin ;;
  deb-dev) DEB_TYPE=dev ;;
  *)       DEB_TYPE=""  ;;
esac

export DEB_TYPE

FILES=$( $FILE_LIST )

if [ $PKG_TYPE = deb-dev ]
then
  NAME=$NAME-dev
fi

if ! [ -f configure ] || [ configure.ac -nt configure ]
then
  ./bootstrap
fi

mkdir -pv /tmp/$USER/mk-src
D=$( mktemp -d /tmp/$USER/mk-src/$NAME-$DEB_TYPE-tgz-XXX )
mkdir -v $D/$NAME-$VERSION
cp --parents $FILES $D/$NAME-$VERSION || \
  {
    echo ""
    echo "mk-src-tgz.sh: " \
         "Some file copy failed! See above for error message."
    exit 1
  }

tar cfz $TGZ -C $D $NAME-$VERSION

echo "Created $PWD $TGZ"
rm -r $D
echo
