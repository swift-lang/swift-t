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

snip 1 examples/5/func.f90
snip 1 examples/5/prog-f90.f90
snip 1 examples/5/prog-swift.swift
snip 1 examples/7/f.c

uptodate swift.html swift.txt || \
asciidoc --attribute stylesheet=${PWD}/swift.css swift.txt

uptodate leaf.html leaf.txt || \
asciidoc --attribute stylesheet=${PWD}/swift.css leaf.txt
