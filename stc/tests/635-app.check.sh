#!/bin/sh
IN="635-rand.tmp"
OUT="635-rand-end.tmp"

if [ ! -f "${OUT}" ]; then
    echo "${OUT} was not created"
    exit 1
fi

if diff ${IN} ${OUT} ; then
    rm "${IN}" "${OUT}"
    exit 0
else
    echo "${OUT} did not have expected contents"
    exit 1
fi
