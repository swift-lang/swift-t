#!/bin/zsh

set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
path+=( $MPICH/bin $TURBINE/bin $STC/bin )

# Build BLAS
if [[ ! -f blas.tgz ]]
then
  mkdir -p /tmp/blas-build
  pushd /tmp/blas-build
  wget http://www.netlib.org/blas/blas.tgz
  tar xfz blas.tgz
  cd BLAS
  for f in *.f
  do
    gfortran -fPIC -c ${f}
  done
  ar cr BLAS.a *.o
  popd
fi

ls /tmp/blas-build

export BLAS=/tmp/blas-build/BLAS/BLAS.a

print HI
./run.sh
