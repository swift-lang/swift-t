#!/bin/sh

DIR="$1"
shift
if [ -n "$DIR" ]
then
    DIR=$DIR
fi

# Refer to GCC docs on -M and -MG
# sed: Take filename without .o and paste $DIR on it
${CC} -M -MG "$@" | sed -e "s@^\(.*\)\.o:@$DIR/\1.d $DIR/\1.o:@"
