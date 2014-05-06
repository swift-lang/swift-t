This is an umbrella project that pulls in all the dependencies required for
Swift/T, an implicitly parallel programming language for composing functions
and command-line executables into massively parallel applications.

If you are not doing development, prepackaged distributions are available
at http://www.mcs.anl.gov/exm/local/guides/swift.html

The Swift/T user guide is currently available at:
http://www.mcs.anl.gov/exm/local/guides/swift.html

Prerequisites
-------------
Tcl8.6, including developer tools.  You need to have tclConfig.sh present
in a lib or lib64 directory of your Tcl installation

E.g. to install on Ubuntu:

    sudo apt-get install tcl8.6 tcl8.6-dev

An implementation of MPI compatible with MPI2+. The easiest way to do this
is to build MPICH from source, for which we provide an automated script.
See below.

Swift/T Quick Build instructions
--------------------------------
These quick build instructions assume you have tcl8.6
installed on your system, and mpich2 or mpich3. They
also assume you are using Linux.  See the Swift/T user
guide for instructions on building for other platforms

Checkout this project:

    git clone https://github.com/timarmstrong/swift-t.git swift-t
    cd swift-t

Checkout all submodule projects:

    git submodule init
    git submodule update

Create a build settings file:

    ./dev/build/init-settings.sh

Inspect dev/build/exm-settings.sh for any settings you may need to change,
for example install location (EXM\_PREFIX), MPI version (MPI\_VERSION), or
source location (EXM\_SRC\_ROOT), then build with

    ./dev/build/build-all.sh

If you encounter an error while building, you may need to modify the
configuration, for example to explicitly specify the location of
Tcl or MPI.

Swift/T components should be installed into the directory specified by
EXM\_PREFIX in the settings file.

You can do a quick build and install of all components using the fast
build script:

    ./dev/build/fast-build-all.sh

MPICH3 Quick Build Instructions
-------------------------------
Download and extract the mpich3 source distribution from:
http://www.mpich.org/downloads/ e.g.

    wget http://www.mpich.org/static/tarballs/3.0.2/mpich-3.0.2.tar.gz
    tar xvzf mpich-3.0.2.tar.gz

Change into the source directory, then use the provided build script
to configure, compile and install mpich3

    cd mpich-3.0.2
    /path/to/swift-t/dev/mpi_build.sh
