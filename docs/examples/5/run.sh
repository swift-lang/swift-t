#!/bin/sh -eu

CFLAGS="-std=gnu99"
echo "Stand-alone"
gcc -c $CFLAGS prog-c.c
sed 's/main(/leaf_main(/' main.c > swift_main.c
gcc -c $CFLAGS swift_main.c
gcc -o prog-c.x prog-c.o swift_main.o
./prog-c.x 10


echo "Swift/T"
sh -eu ./test-main.sh
