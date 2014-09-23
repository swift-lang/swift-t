rm *.o
swig -module b b.i
gcc -c -fPIC b.c
gcc -c -fPIC -I $TCL_INCLUDE b_wrap.c
gcc -shared -o libb.so b_wrap.o b.o
tclsh make-package.tcl > pkgIndex.tcl
