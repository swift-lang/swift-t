#!/bin/zsh -e

STC_DOCS=$( dirname $0 )
cd ${STC_DOCS}

./make-stc-docs.zsh

DEST=wozniak@login.mcs.anl.gov:/mcs/web/research/projects/exm/local/guides

rsync leaf.html examples.tar.gz *.png ${DEST}
