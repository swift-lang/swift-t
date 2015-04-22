#!/usr/bin/env bash

rm -f 692-in.txt

for i in $(seq 0 31); do
    f="692-out-${i}.txt"
    if [ ! -f "$f" ]; then
        echo "Expected $f to exist"
        exit 1;
    fi

    if [ "$(cat $f)" != "692" ]; then
        echo "$f had wrong contents"
        exit 1;
    fi
    rm $f
done
