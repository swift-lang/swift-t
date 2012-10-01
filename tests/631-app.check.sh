#!/bin/sh
if 
if [ ! -f "630-outfile.txt" ]; then
    echo "630-outfile.txt was not created"
    exit 1
fi

contents=`cat "630-outfile.txt"`
exp_contents='
if [ "$contents" = "hello world!" ] ; then
    rm bob.txt alice.txt
    exit 0
else
