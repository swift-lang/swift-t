Swift/T is an implicitly parallel programming language for composing functions
and command-line executables into massively parallel applications.

The Swift/T user guide and prepackaged distributions are available online at
the Swift/T homepage: http://swift-lang.org/Swift-T .  We recommend
that you start there if you are new to Swift or Swift/T.


MPICH3 Quick Build Instructions
-------------------------------
Uncomment the `MPI_INSTALL` setting in exm-settings.sh:

    MPI_INSTALL=${EXM_PREFIX}/mpi

Download and extract the mpich3 source distribution from:
http://www.mpich.org/downloads/ e.g.

    wget http://www.mpich.org/static/downloads/3.1/mpich-3.1.tar.gz
    tar xvzf mpich-3.1.tar.gz

Change into the source directory, then use the provided build script
to configure, compile and install mpich3:

    cd mpich-3.1
    /path/to/swift-t/dev/build/mpich-build.sh

Build System Details
--------------------
Swift/T is comprised of several modules, which use two different build systems:
autotools/configure/make for C/C++/Fortran modules, and ant for Java modules.
You can build the modules separately, but the scripts and configuration files
in exm-setting.sh speed up the process and make it easier to consistently
configure and build the system.  `exm-settings.sh` controls the build
configuration, and build scripts for each module (e.g. `turbine-build.sh`)
build that module from the module's source directory.  The two most useful
helper scripts are `fast-build-all.sh`, for quickly rebuilding all modules,
and `rebuild-all.sh`, to do a complete reconfiguration and build, including
running autotools.

Add JVM Scripting Plugin
------------------------
- Clone the C-JVM-Scripting project in the root diretory (default setting):
``` git clone https://github.com/isislab-unisa/swift-lang-swift-t-jvm-engine.git ```
- Others instructions will appear ...
Documentation
-------------
The main documentation for Swift/T is under `stc/docs`.  The documentation
is in `asciidoc` format.  You can look at the main documentation in text
format in `swift.txt`.  You can also compile the documentation to html
by running:

  ./make-stc-docs.zsh

A sites guide that provides guidance for configuring Swift/T on various
systems is at `turbine/docs/sites.txt`.
