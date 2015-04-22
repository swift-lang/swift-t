#!/bin/sh

DIR=./dir_675
FILE=test_675.tgz
if [ ! -d $DIR ]; then
    echo "$DIR was not created"
    exit 1
fi

if [ ! -f $DIR/$FILE ]; then
    echo "$DIR/$FILE was not created"
    exit 1
fi

rm $DIR/$FILE
rmdir $DIR
rm ./$FILE
