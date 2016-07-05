#!/bin/zsh

set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install

PATH=${MPICH}/bin:${PATH}
PATH=${TURBINE}/bin:${PATH}
PATH=${STC}/bin:${PATH}

print "Settings:"
which mpicc
which mpiexec
# cat ${TURBINE}/scripts/turbine-build-config.sh
print

# Build BLAS
BV=3.5.0
export BLAS=/tmp/exm-blas-build/BLAS-$BV/BLAS.a
if [[ ! -f ${BLAS} ]]
then
  print "Downloading BLAS..."
  mkdir -p /tmp/exm-blas-build
  pushd /tmp/exm-blas-build
  rm -fv blas.tgz
  wget http://www.netlib.org/blas/blas.tgz
  tar xfz blas.tgz
  cd BLAS-$BV
  for f in *.f
  do
    gfortran -fPIC -c ${f}
  done
  ar cr BLAS.a *.o
  print "BLAS successfully installed in ${PWD}"
  popd
fi

# Get FortWrap
if [[ ! -f /tmp/exm-fortwrap/fortwrap-1.0.4/fortwrap.py ]]
then
  print "Downloading FortWrap"
  mkdir -p /tmp/exm-fortwrap
  pushd /tmp/exm-fortwrap
  wget http://downloads.sourceforge.net/project/fortwrap/fortwrap-1.0.4/fortwrap-1.0.4.tar.gz
  tar xfz fortwrap-1.0.4.tar.gz
  print "FortWrap successfully installed in ${PWD}/fortwrap-1.0.4"
  popd
fi
path+=/tmp/exm-fortwrap/fortwrap-1.0.4

# Run the examples suite:
./run.sh
