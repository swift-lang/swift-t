#!/bin/sh
set -eu

OUT="631-outfile.txt"

if [ ! -f "${OUT}" ]; then
    echo "${OUT} was not created"
    exit 1
fi

if [ -h 631-app-cat.sh ]; then
    rm 631-app-cat.sh
fi

contents=`cat "${OUT}"`
exp_contents='Hello World
hello some text
1'

if [ "$contents" = "${exp_contents}" ] ; then
    rm "${OUT}"
else
    echo "${OUT} did not have expected contents"
    exit 1
fi

rm helloworld.txt
