
This package may be built with Eclipse or with the
autotools-based scripts.

To use ADLB-TCL:

First, compile ADLB as a shared library:
Add the following to the ADLB Makefile
CFLAGS=-fPIC
libadlb.so: $(OBJS)
            gcc -shared -o $(@) -fPIC $(OBJS)

Compile MPICH as a shared library
configure --enable-shared

To build:

Type

./setup.sh
./configure --with-woztools=<PATH/TO/WOZTOOLS> \
            --with-adlb=<PATH/TO/ADLB>
            --with-mpi=<PATH/TO/MPICH>
            --with-tcl=<PATH/TO/TCL>
make

Remember to use config.status for speed

Creates a library in lib/

See header of configure.ac for configuration options.
