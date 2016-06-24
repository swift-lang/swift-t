#!/bin/zsh
set -eu

# Jenkins script - build only

print JENKINS.ZSH

source maint/jenkins-configure.sh

cat export/blob.swift

rm -rf autom4te.cache
./bootstrap

./configure --prefix=$TURBINE        \
            --with-tcl=/usr          \
            --with-mpi=$MPICH        \
            --with-c-utils=$C_UTILS  \
            --with-adlb=$ADLB        \
            --with-hdf5=no           \
            --disable-static-pkg     \
            --disable-static

make clean

make V=1

make V=1 install
