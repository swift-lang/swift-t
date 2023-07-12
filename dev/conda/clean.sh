#!/bin/zsh -f
set -eu

# CLEAN SH
# Clean generated files

rm0()
# File removal, ok with empty argument list
# Safer than rm -f
{
  local F V
  zparseopts -D -E v=V
  for F in ${*}
  do
    if [[ -f $F ]] rm ${V} $F
  done
}

# Get this directory:
THIS=${0:h:A}
cd $THIS

for D in *(/)
do
  rm0 -v $D/settings.sed $D/meta.yaml
done
