#!/bin/zsh

set -eu

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install

ls /tmp
ls $MPICH
ls $MPICH/bin

PATH=${MPICH}/bin:${PATH}
PATH=${TURBINE}/bin
PATH=${STC}/bin:${PATH}

echo $PATH


which mpicc
which mpiexec



cat ${TURBINE}/scripts/turbine-build-config.sh

# Build BLAS
export BLAS=/tmp/exm-blas-build/BLAS/BLAS.a
if [[ ! -f ${BLAS} ]]
then
  mkdir -p /tmp/exm-blas-build
  pushd /tmp/exm-blas-build
  rm -fv blas.tgz
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

# Get FortWrap
if [[ ! -f /tmp/exm-fortwrap/fortwrap-1.0.4/fortwrap.py ]]
then
  mkdir -p /tmp/exm-fortwrap
  pushd /tmp/exm-fortwrap
  wget http://downloads.sourceforge.net/project/fortwrap/fortwrap-1.0.4/fortwrap-1.0.4.tar.gz
  tar xfz fortwrap-1.0.4.tar.gz
  popd
fi
path+=/tmp/exm-fortwrap/fortwrap-1.0.4

ls /tmp/blas-build

print HI
./run.sh
