#!/bin/sh
OUT="tail.txt"

if [ ! -f "${OUT}" ]; then
    echo "${OUT} was not created"
    exit 1
fi

if [ -h 632-split.sh ]; then
    rm 632-split.sh
fi

contents=`cat "${OUT}"`
exp_contents='three
four
five
six'

if [ "$contents" = "${exp_contents}" ] ; then
    rm "${OUT}"
    exit 0
else
    echo "${OUT} did not have expected contents"
    exit 1
fi

rm lines.txt tail.txt
