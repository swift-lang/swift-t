rm *.o
swig -module b b.i
gcc -c -fPIC b.c
gcc -c -fPIC $TCL_INCLUDE_SPEC b_wrap.c
gcc -shared -o libb.so b_wrap.o b.o
tclsh make-package.tcl > pkgIndex.tcl
