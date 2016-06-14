#!/bin/zsh -e

# DO NOT USE

# INSTALL STC DOCS

# Builds and installs STC docs.
# Note: This just copies everything to the Swift WWW directory.
# You still have to commit it there.
# Edit DEST

STC_DOCS=$( dirname $0 )
cd ${STC_DOCS}

./make-stc-docs.zsh

DEST=${HOME}/proj/swift-www/Swift-T

cp -uv examples.tar.gz ${DEST}/downloads
cp -uv *.html *.png ${DEST}
