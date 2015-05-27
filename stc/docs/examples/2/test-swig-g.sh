#!/bin/sh
set -ex

gcc -c -fPIC -Wall g.c
gcc -c -fPIC -I ${TCL_HOME}/include g_wrap.c
gcc -shared -o libg.so g_wrap.o g.o
tclsh make-package.tcl > pkgIndex.tcl
