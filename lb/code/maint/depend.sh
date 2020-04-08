#!/bin/bash

# Autogenerates Makefile dependencies
# See the GCC documentation for -M, -MG
# This does not work with XLC- on BlueGene, always make clean

DIR="$1"
shift
if [ -n "$DIR" ] ; then
    DIR="$DIR"
fi

# Skip if using XLC
[[ ${CC} == *xlc* ]] && exit 0

${CC} -M -MG "$@" | sed -e "s@^\(.*\)\.o:@$DIR\1.d $DIR\1.o:@"
