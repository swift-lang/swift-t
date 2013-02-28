This is an umbrella project that pulls in all the dependencies required for
Swift/T, an implicitly parallel programming language for composing functions
and command-line executables into massively parallel applications.

If you are not doing development, prepackaged distributions are available
at http://www.mcs.anl.gov/exm/local/guides/swift.html

The Swift/T user guide is currently available at:
http://www.mcs.anl.gov/exm/local/guides/swift.html

Prerequisites
-------------
Tcl8.5, including developer tools.  You need to have tclConfig.sh present
in a lib or lib64 directory of your Tcl installation

Ubuntu:
  sudo apt-get install tcl8.5 tcl8.5-dev


An implementation of MPI compatible with MPI2+. The easiest way to do this
is to build MPICH from source, for which we provide an automated script.
See below.

Swift/T Quick Build instructions
--------------------------------
These quick build instructions assume you have tcl8.5
installed on your system, and mpich2 or mpich3. They
also assume you are using Linux.  See the Swift/T user
guide for instructions on building for other platforms

Checkout this project
  git clone git@github.com:timarmstrong/swift-t.git swift-t
  cd swift-t

Checkout all submodule projects

  git submodule init
  git submodule update

Add symlinks to mpi and tcl if not present

  mkdir inst
  pushd inst
  ln -s /path/to/tcl tcl  # For ubuntu, /path/to/tcl is /usr
  ln -s /path/to/mpich mpich
  popd

Now use the build script to configure, build and install

  pushd dev
  ./rebuild_all.sh
  popd

Swift/T components should be installed into the inst directory.

  ls inst

You can do a quick build and install of all components using
the fast-build script:

  cd dev
  ./fast-build.sh
  



To recompile and inst

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
