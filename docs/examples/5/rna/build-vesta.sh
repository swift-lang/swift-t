#!/bin/bash

#set -x

OMP=-fopenmp
#OMP=

TCL_INSTALL=/home/wozniak/Public/tcl-8.5.12-bgq
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ -I $TCL_INSTALL/include -fPIC -g -c extension.cpp
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ $OMP -fPIC -c thfribo-main.cpp
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ -shared -o libthfribo_main.so $OMP thfribo-main.o extension.o -L $TCL_INSTALL/lib -l tcl8.5 -Wl,-rpath -Wl,$TCL_INSTALL/lib
$TCL_INSTALL/bin/tclsh8.5 make-package.tcl > pkgIndex.tcl
stc -t checkpointing -r $PWD thfribo-swift.swift
