#!/bin/bash

# Autogenerates Makefile dependencies
# See the GCC documentation for -M, -MG
# This does not work with XLC - set DEPCC to gcc

DIR="$1"
shift
if [ -n "$DIR" ] ; then
    DIR="$DIR"
fi

${DEPCC} -M -MG "$@" | sed -e "s@^\(.*\)\.o:@$DIR\1.d $DIR\1.o:@"
