#!/bin/bash
set -eu
set -o pipefail

# Autogenerates Makefile dependencies
# See the GCC documentation for -M, -MG
# This does not work with XLC - set DEPCC=gcc

DIR="$1"
shift

# This sed command replaces the directory component of the
#      target file with a standardized directory in DIR,
#      and add the .d file to the dependency list
${DEPCC} -M -MG "$@" | sed -e "s@^\(.*\)\.o:@$DIR\1.d $DIR\1.o:@"
