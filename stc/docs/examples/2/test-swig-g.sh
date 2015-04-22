#!/bin/sh
rm -fv *.o
set -ex
gcc -c -fPIC -Wall g.c
gcc -c -fPIC -I/home/wozniak/sfw/tcl-8.6.0/include g_wrap.c
gcc -shared -o libg.so g_wrap.o g.o
tclsh make-package.tcl > pkgIndex.tcl
