#!/bin/zsh
set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc

PATH=${TURBINE}/bin:${PATH}
PATH=${STC}/bin:${PATH}

print "Settings:"
which mpicc mpiexec swift-t stc

print

# Build BLAS
BV=3.6.0 # BLAS Version
export BLAS=/tmp/exm-blas-build/BLAS-$BV/BLAS.a
if [[ -f ${BLAS} ]]
then
  print "Found BLAS: ${BLAS}"
else
  print "Downloading BLAS..."
  mkdir -p /tmp/exm-blas-build
  pushd /tmp/exm-blas-build
  rm -fv blas.tgz
  wget http://www.netlib.org/blas/blas-$BV.tgz
  tar xfz blas-$BV.tgz
  cd BLAS-$BV
  print "Compiling BLAS..."
  for f in *.f
  do
    gfortran -fPIC -c ${f}
  done
  ar cr BLAS.a *.o
  print "BLAS successfully installed in ${PWD}"
  popd
fi

# Install FortWrap here:
FV=git # FortWrap Version
path+=/tmp/exm-fortwrap-${FV}
if [[ -e /tmp/exm-fortwrap-${FV}/fortwrap.py ]]
then
  print "Found FortWrap: $( which fortwrap.py )"
else
  print "Downloading FortWrap"
  mkdir -p /tmp/exm-fortwrap-${FV}
  pushd /tmp/exm-fortwrap-${FV}
  # wget http://downloads.sourceforge.net/project/fortwrap/fortwrap-1.0.4/fortwrap-1.0.4.tar.gz
  # tar xfz fortwrap-1.0.4.tar.gz
  wget https://raw.githubusercontent.com/mcfarljm/fortwrap/master/fortwrap.py
  print "FortWrap successfully installed in /tmp/exm-fortwrap-${FV}"
  popd
fi

# Run the examples suite:
./run.sh
