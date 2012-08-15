#!/bin/sh

DIR="$1"
shift
if [ -n "$DIR" ] ; then
    DIR="$DIR"
fi

exec $CC -M -MG "$@" | sed -e "s@^\(.*\)\.o:@$DIR\1.d $DIR\1.o:@"
