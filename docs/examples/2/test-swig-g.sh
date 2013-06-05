rm *.o
gcc -fPIC -c g.c &&
gcc -fPIC -I /usr/include/tcl8.5 -c g_wrap.c &&
gcc -shared -o libg.so g_wrap.o g.o &&
tclsh make-package.tcl > pkgIndex.tcl


