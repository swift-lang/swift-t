#!/usr/bin/env bash

f1="691-tmp.txt"
f2="691-tmp2.txt"
f3="691-tmp3.txt"

for f in $f1 $f2 $f3; do 
    if [ ! -f "$f" ]; then
        echo "Expected $f to exist"
        exit 1;
    fi
    contents=`cat $f`
    if [ "$contents" != "contents." ]; then
        echo "Contents of $f don't match expected"
        exit 1;
    fi
    rm $f
done
