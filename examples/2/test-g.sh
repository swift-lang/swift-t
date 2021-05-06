#!/bin/sh -ex

# Run g as a stand-alone program

gcc -c g.c
gcc -c test-g.c
gcc -o g.x test-g.o g.o

./g.x
