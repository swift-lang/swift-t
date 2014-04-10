TCL_INSTALL=/home/wozniak/Public/sfw/x86/tcl-8.5.12
g++ -I $TCL_INSTALL/include/ -fPIC -g -c extension.cpp
g++ -fopenmp -fPIC -c thfribo-main.cpp
g++ -fopenmp -shared -o libthfribo_main.so thfribo-main.o extension.o -L $TCL_INSTALL/lib/ -l tcl8.5 -Wl,-rpath -Wl,$TCL_INSTALL/lib 
tclsh make-package.tcl > pkgIndex.tcl
stc -j /home/wozniak/Public/sfw/x86/jdk1.7.0_25/bin/java -r $PWD thfribo-swift.swift
