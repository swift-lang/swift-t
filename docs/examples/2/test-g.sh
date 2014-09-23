#!/bin/sh -e

gcc -c g.c 
gcc -c test-g.c 
gcc -o g.x test-g.o g.o 

./g.x
