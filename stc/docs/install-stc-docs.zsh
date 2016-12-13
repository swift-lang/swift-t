#!/bin/zsh
set -eu

# INSTALL STC DOCS

# OBSOLETE
# Builds and installs STC docs.
# Note: This just copies everything to the MCS ExM WWW directory.
# Edit DEST to change this

DEST=${HOME}/exm.www

STC_DOCS=$( dirname $0 )
cd ${STC_DOCS}

./make-stc-docs.zsh

cp -uv examples.tar.gz ${DEST}/downloads
cp -uv *.html *.png ${DEST}/guides
