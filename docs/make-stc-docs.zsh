#!/bin/zsh -e

examples/snippet.pl -n=1 < examples/6/f.c > examples/6/f-snip-1.c

asciidoc --attribute stylesheet=${PWD}/swift.css swift.txt
asciidoc --attribute stylesheet=${PWD}/swift.css leaf.txt
