#!/usr/bin/env bash
if [ $# -ne 4 ]; then
    echo "Wrong number of args: $#"
    exit 1
fi
in=$1
lines=$2
out1=$3
out2=$4

linesplusone=$(($lines + 1))
head -n "$lines" "$in" > "$out1"
tail -n "+$linesplusone" "$in" > "$out2"

