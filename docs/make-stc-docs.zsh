#!/bin/zsh -e

# MAKE-STC-DOCS

# 1) Extracts snippets for code samples
# 2) Runs asciidoc

snip()
{
  N=$1
  FILE=$2
  # Insert -snip-NUMBER into file name
  OUTPUT=${FILE/\./-snip-${N}.}
  examples/snippet.pl -n=${N} ${FILE} > ${OUTPUT}
}

snip 1 examples/6/f.c
snip 1 examples/8/func.f90
snip 1 examples/8/prog-f90.f90
snip 1 examples/8/prog-swift.swift

uptodate swift.html swift.txt || \
asciidoc --attribute stylesheet=${PWD}/swift.css swift.txt

uptodate leaf.html leaf.txt || \
asciidoc --attribute stylesheet=${PWD}/swift.css leaf.txt
