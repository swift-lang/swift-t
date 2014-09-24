#!/bin/sh -eu

echo "Stand-alone"
CFLAGS="-std=gnu99"
gcc $CFLAGS -o main.x main.c
./main.x 10 11 12

echo "Swift/T"
sh -eu ./test-main.sh
