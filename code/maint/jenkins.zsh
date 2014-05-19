#!/bin/zsh

# ADLB JENKINS.ZSH
# Run on the Jenkins server
# Installs ADLB; runs its tests

set -eu

df /tmp

if [[ ! -d /tmp/mpich-install ]]
then
  print "MPICH disappeared!"
  print "You must manually run the MPICH Jenkins test to restore MPICH"
  exit 1
fi

PATH=/tmp/mpich-install/bin:$PATH
which mpicc
mpicc -show

./setup.sh
mkdir -p /tmp/exm-install
./configure CC=$(which mpicc) --prefix=/tmp/exm-install/lb --with-c-utils=/tmp/exm-install/c-utils
make clean
rm -rf autom4te.cache
rm -rf /tmp/exm-install/lb
make V=1 install
make V=1 apps/batcher.x
ldd apps/batcher.x
exit 0
