#!/usr/bin/env bash

for i in $(seq 0 31); do
    f="f-${i}.txt"
    if [ ! -f "$f" ]; then
        echo "Expected $f to exist"
        exit 1;
    fi
    rm $f
done
