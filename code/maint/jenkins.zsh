#!/bin/zsh

# ADLB JENKINS.ZSH
# Run on the Jenkins server
# Installs ADLB; runs its tests

set -eu

echo
echo "maint/jenkins.zsh ..."
echo

if [[ ! -d /tmp/mpich-install ]]
then
  print "MPICH disappeared!"
  print "You must manually run the MPICH Jenkins test to restore MPICH"
  exit 1
fi

rm -rf autom4te.cache
rm -rf /tmp/exm-install/lb

set -x
PATH=/tmp/mpich-install/bin:$PATH

echo MPICC:
which mpicc
mpicc -show
echo

./bootstrap
mkdir -p /tmp/exm-install
./configure CC=$(which mpicc) --prefix=/tmp/exm-install/lb --with-c-utils=/tmp/exm-install/c-utils
echo
make clean
make V=1 install
# ldd lib/libadlb.so
# make V=1 apps/batcher.x
# ldd apps/batcher.x

make V=1 tests
make V=1 test_results

exit 0
