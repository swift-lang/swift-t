#!/bin/sh

gcc -c g.c || exit 1
gcc -c test-g.c || exit 1
gcc -o g.x test-g.o g.o || exit 1

./g.x
