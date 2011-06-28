
This package may be built with Eclipse or with the
autotools-based scripts.

To use ADLB-TCL, you must first compile/install TCL, ADLB, MPICH, and
woztools.

First, compile ADLB as a shared library:
Add the following to the ADLB Makefile
CFLAGS=-fPIC
libadlb.so: $(OBJS)
            gcc -shared -o $(@) -fPIC $(OBJS)

Compile MPICH as a shared library
configure --enable-shared

Compile woztools as a shared library
configure --enable-shared
make woztools_so

To build:

Type

./setup.sh
./configure --with-woztools=<PATH/TO/WOZTOOLS> \
            --with-adlb=<PATH/TO/ADLB>
            --with-mpi=<PATH/TO/MPICH>
            --with-tcl=<PATH/TO/TCL>
make package

Remember to use config.status for speed

Creates a library in lib/

See header of configure.ac for configuration options.

The big picture is to be able to run SPMD TCL scripts that use the
Turbine and ADLB TCL packages, which are installed in lib.

To do this, you run bin/turbine <script>.tcl

bin/turbine is a shell script that sets up the paths
for tclsh.  This allows the user script to "package require"
the Turbine and ADLB TCL packages.  These packages are
built around core shared libraries, which wrap up the native
functionality of their respective C codes.

Example user scripts are in turbine/test .

