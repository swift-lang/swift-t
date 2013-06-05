rm *.o
gcc -c -fPIC  g.c &&
gcc -c -fPIC -I /usr/include/tcl8.5 g_wrap.c &&
gcc -shared -o libg.so g_wrap.o g.o &&
tclsh make-package.tcl > pkgIndex.tcl


