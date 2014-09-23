#!/bin/sh -eu

CFLAGS="-std=gnu99"
echo "Stand-alone"
gcc -c $CFLAGS test-main.c
sed 's/main(/leaf_main(/' main.c > leaf_main.c
gcc -c $CFLAGS leaf_main.c
gcc -o test-main.x test-main.o leaf_main.o
./test-main.x 10

echo "Swift/T"
sh -eu ./test-main.sh
