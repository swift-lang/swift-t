rm *.o
swig -module b b.h
gcc -c -fPIC b.c
gcc -c -fPIC -I /usr/include/tcl8.5 b_wrap.c
gcc -shared -o libb.so b_wrap.o b.o
tclsh make-package.tcl > pkgIndex.tcl
