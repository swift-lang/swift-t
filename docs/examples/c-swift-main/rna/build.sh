g++ -I /home/ketan/tcl-install/include/ -fPIC -g -c extension.cpp
g++ -fopenmp -fPIC -c thfribo-main.cpp
g++ -fopenmp -shared -o libthfribo_main.so thfribo-main.o extension.o -L /home/ketan/tcl-install/lib/ -l tcl8.5 -Wl,-rpath -Wl,/home/ketan/tcl-install/lib
tclsh make-package.tcl > pkgIndex.tcl
stc -j /home/ketan/jdk1.7.0_07/bin/java -r $PWD thfribo-swift.swift
