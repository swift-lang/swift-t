#!/bin/zsh

set -e

examples/snippet.pl -n=1 < examples/6/f.c > examples/6/f-snip-1.c

asciidoc leaf.txt

DEST=wozniak@login.mcs.anl.gov:/mcs/web/research/projects/exm/local/guides

rsync leaf.html examples.tar.gz *.png ${DEST}
