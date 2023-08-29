#!/bin/zsh
set -eu

# CONDA INSTALL
# Install script for use by maintainers when testing PKGs.
# Normally you will want to use a different fresh Conda
#          from the Conda you used to build the PKG.
# Provide the PKG on the command line.
# NOTE: conda install from file does not install dependencies!
#       Cf. https://docs.anaconda.com/free/anaconda/packages/install-packages
# NOTE: Keep LIST in sync with meta.yaml
# USAGE: Provide PKG
#        Provide -R to install R
#        Provide -D to skip installing dependencies

# If the user requested an R installation,
# variable R will be set to the package name for R:
D="" R=""
zparseopts -D -E D=D R=R
if (( ${#R} )) R="r"

if (( ${#*} != 1 )) {
  print "Provide PKG!"
  return 1
}
PKG=$1

zmodload zsh/stat
zstat -H A -F "%Y-%m-%d %H:%M" $PKG
print ${A[mtime]} ${A[size]} $PKG
printf "md5sum: "
md5sum $PKG

which conda
conda env list

USE_GCC="gcc"
# Skip GCC on osx-64
if [[ $PKG =~ "/osx-64/" ]] USE_GCC=""

LIST=(
  ant
  autoconf
  $USE_GCC
  make
  mpich-mpicc
  openjdk
  python
  swig
  zsh
  $R
)

set -x
if (( ! ${#D} )) conda install --yes -c conda-forge $LIST
conda install --yes $PKG
