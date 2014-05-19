#!/bin/zsh

# ADLB JENKINS.ZSH
# Run on the Jenkins server
# Installs ADLB; runs its tests

set -eu

df /tmp

ls /tmp



if [[ ! -f /tmp/mpich-install ]]
then
  print "MPICH disappeared!"
  print "You must manually run the MPICH Jenkins test to restore MPICH"
  exit 1
fi

PATH=/tmp/mpich-install/bin:$PATH
ls /tmp/mpich-install
which mpicc
mpicc -show


set -eu

ls
pwd
cd code
./setup.sh
mkdir -p /tmp/exm-install
which mpicc
# wget -O Makefile      http://dl.dropbox.com/u/1739272/Makefile
# wget -O Makefile.in   http://dl.dropbox.com/u/1739272/Makefile.in
# Check your mpicc location first!
./configure CC=$(which mpicc) --prefix=/tmp/exm-install/lb --with-c-utils=/tmp/exm-install/c-utils
make clean
rm -rf autom4te.cache
rm -rf /tmp/exm-install/lb
make V=1 install
ls /tmp/exm-install/lb -thor

exit 0
#The rest is debug stuff i think, Justin you can change this anytime
cat Makefile.in
cat Makefile
make V=1 >& make.out
cp make.out /tmp/exm-install/lb
make V=1 install >& make_install.out
cp make_install.out /tmp/exm-install/lb
make V=1 apps/batcher.x >& make_batcher.out
ldd apps/batcher.x
cp make_batcher.out /tmp/exm-install/lb

