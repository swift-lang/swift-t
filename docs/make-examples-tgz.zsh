#!/bin/zsh

# Pack example programs for WWW

set -e

if [[ ! -d examples ]]
then
  print "examples directory not found!"
  exit 1
fi

print cleaning...
examples/clean.sh
print

TGZ=examples.tar.gz

find examples/[1-9] -maxdepth 1   \
              -name "*.sh"     -o \
              -name "*.f90"    -o \
              -name "*.[chfi]" -o \
              -name "*.tcl"    -o \
              -name "*.swift"  -o \
              -name "Makefile"    \
      | \
      xargs tar cfzv ${TGZ}

du -h ${TGZ}
