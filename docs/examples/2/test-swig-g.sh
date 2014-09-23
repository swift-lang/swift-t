#!/bin/sh -e
rm *.o
gcc -c -fPIC  g.c
TCL_INCLUDE=$HOME/sfw/tcl-8.6.0/include
gcc -c -fPIC -I $TCL_INCLUDE g_wrap.c
gcc -shared -o libg.so g_wrap.o g.o
tclsh make-package.tcl > pkgIndex.tcl
