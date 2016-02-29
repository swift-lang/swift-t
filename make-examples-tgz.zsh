#!/bin/zsh

# MAKE-EXAMPLES-TGZ.ZSH
# Pack example leaf functions for WWW

set -eu

if [[ ! -d examples ]]
then
  print "examples directory not found!"
  exit 1
fi

print cleaning...
examples/clean.sh
print

TGZ=examples.tar.gz

date "+%m/%d/%Y %I:%M%p" > examples/timestamp.txt

FILES=( examples/timestamp.txt )
FILES+=( $( find examples/[1-9] -maxdepth 1   \
              -name "*.sh"     -o             \
              -name "*.f90"    -o             \
              -name "*.[chfi]" -o             \
              -name "*.cxx"    -o             \
              -name "*.tcl"    -o             \
              -name "*.swift"  -o             \
              -name "Makefile"
      ) )

tar cfzv ${TGZ} ${FILES}
du -h ${TGZ}
