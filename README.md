This is an umbrella project that pulls in all the dependencies required for
Swift/T, an implicitly parallel programming language for composing functions
and command-line executables into massively parallel applications.

If you are not doing development, prepackaged distributions are available
at http://www.mcs.anl.gov/exm/local/guides/swift.html

The Swift/T user guide is available online at:
http://www.mcs.anl.gov/exm/local/guides/swift.html

You can find the documentation corresponding to this version of Swift/T
in the stc sub-project under docs.  See Documentation section for
instructions on building.

Prerequisites
-------------
Tcl8.6, including developer tools.  You need to have tclConfig.sh present
in a lib or lib64 directory of your Tcl installation

E.g. to install on Ubuntu:

    sudo apt-get install tcl8.6 tcl8.6-dev

An implementation of MPI compatible with MPI 2 or greater is required.
MPI 3.0 or greater compatibility is recommended.  If you are installing on
a cluster or other system, you can configure Swift/T to be built with that
version of MPI.  If you are installing on another style of system without
MPI preinstalled, the recommended way way to get an up-to-date version of
MPI is to general way to to build MPICH from source, for which we provide
an automated script, as described in a later section.  Your system may
supply a prepackaged distribution, e.g. the `mpich2` and `libmpich2-dev`
packages on Ubuntu.
Swift/T will generally work fine with these, but many distributions are
slow to update their MPI package.

Swift/T Quick Build instructions
--------------------------------
These quick build instructions assume you have tcl8.6 or greater
and an MPI distribution that supports the MPI 2 or MPI 3.0 standards.
We do not cover building for all possible systems: if you encounter
a problem, the Swift/T user guide has instructions on building on
specific systems.

Checkout this project:

    git clone https://github.com/swift-lang/swift-t.git swift-t
    cd swift-t

Checkout all submodule projects:

    git submodule init
    git submodule update

Create a build settings file:

    ./dev/build/init-settings.sh

Open dev/build/exm-settings.sh in a text editor to update any settings
You should update these settings at a minimum to set install and source
locations:
    
    EXM_PREFIX=/path/to/install
    EXM_SRC_ROOT="${SCRIPT_DIR}/../.."

If you using an MPI 2.x but not MPI 3.0 compatible distribution, you will
need to set:
 
    MPI_VERSION=2

You can build with this command:

    ./dev/build/build-all.sh

The build script is often able to locate all dependencies without
further explicit configuration.  If you encounter an error while
building, or want to ensure that a specific version is used, you can
modify configuration, for example to explicitly set the location of
Tcl or MPI.

After this initial build, you can do a quick build and install of
all components using the fast build script:

    ./dev/build/fast-build-all.sh

For more information on the build system, see the later section on
build system details.

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
Swift/T is comprised of several modules, whichuse two different build systems:
autotools/configure/make for C/C++/Fortran modules, and ant for Java modules.
You can build the modules separately, but the scripts and configuration files
in exm-setting.sh speed up the process and make it easier to consistently
configure and build the system.  `exm-settings.sh` controls the build
configuration, and build scripts for each module (e.g. `turbine-build.sh`)
build that module from the module's source directory.  The two most useful
helper scripts are `fast-build-all.sh`, for quickly rebuilding all modules,
and `rebuild-all.sh`, to do a complete reconfiguration and build, including
running autotools.

Documentation
-------------
The main documentation for Swift/T is under `stc/docs`.  The documentation
is in `asciidoc` format.  You can look at the main documentation in text
format in `swift.txt`.  You can also compile the documentation to html
by running:

  ./make-stc-docs.zsh 

A sites guide that provides guidance for configuring Swift/T on various
systems is at `turbine/docs/sites.txt`.
