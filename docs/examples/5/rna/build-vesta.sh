#!/bin/bash

#set -x

TCL_INSTALL=/home/wozniak/Public/tcl-8.5.12-bgq
#g++ -I $TCL_INSTALL/include/ -fPIC -g -c extension.cpp
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ -I $TCL_INSTALL/include -fPIC -g -c extension.cpp
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ -fopenmp -fPIC -c thfribo-main.cpp
#g++ -fopenmp -shared -o libthfribo_main.so thfribo-main.o extension.o -L $TCL_INSTALL/lib/ -l tcl8.5 -Wl,-rpath -Wl,$TCL_INSTALL/lib 
/bgsys/drivers/ppcfloor/gnu-linux/bin/powerpc64-bgq-linux-g++ -fopenmp -shared -o libthfribo_main.so thfribo-main.o extension.o -L $TCL_INSTALL/lib -l tcl8.5 -Wl,-rpath -Wl,$TCL_INSTALL/lib
$TCL_INSTALL/bin/tclsh8.5 make-package.tcl > pkgIndex.tcl
stc -t checkpointing -r $PWD thfribo-swift.swift
