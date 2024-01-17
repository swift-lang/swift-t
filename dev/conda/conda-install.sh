#!/bin/zsh
set -eu

# CONDA INSTALL
# Install script for use by maintainers when testing PKGs.
# Normally you will want to use a different fresh Conda
#          from the Conda you used to build the PKG.
# Provide the PKG on the command line.
# NOTE: conda install from file does not install dependencies!
#       Cf. https://docs.anaconda.com/free/anaconda/packages/install-packages
#       Thus this script installs dependencies using PLATFORM/deps.sh
# NOTE: Keep LIST in sync with meta.yaml
# USAGE: Provide PKG
#        Provide -r to install R
#        Provide -D to skip installing dependencies

D="" R=""
zparseopts -D -E D=D r=R

# Default behavior:
INSTALL_DEPS=1
USE_R=0

# Handle user flags:
if (( ${#D} )) INSTALL_DEPS=0
if (( ${#R} )) USE_R=1

if (( ${#*} != 1 )) {
  print "Provide PKG!"
  return 1
}
PKG=$1

# Get this directory (absolute):
DEV_CONDA=${0:A:h}

# Report information about given PKG:
zmodload zsh/stat zsh/mathfunc
zstat -H A -F "%Y-%m-%d %H:%M" $PKG
printf "%s %.1f MB %s\n" \
       ${A[mtime]} $(( float(${A[size]}) / (1024*1024) )) $PKG
printf "md5sum: "
md5sum $PKG

# Report information about active Python/Conda:
if ! which conda >& /dev/null
then
  print "No conda!"
  return 1
fi

print
print "using python:" $( which python )
print "using conda: " $( which conda )
print

conda env list

# PKG is of form
# ANACONDA/conda-bld/PLATFORM/swift-t-V.V.V-pyVVV.tar.bz2
# Pull out PLATFORM (head then tail):
PLATFORM=${PKG:h:t}
print "platform: $PLATFORM"

set -x
# Defaults:
USE_ANT=1
USE_GCC=1
USE_ZSH=1

source $DEV_CONDA/$PLATFORM/deps.sh

# Build dependency list:
LIST=()
if (( USE_ANT )) LIST+=ant
if (( USE_GCC )) LIST+=gcc
if (( USE_ZSH )) LIST+=zsh
LIST+=(
  autoconf
  make
  mpich-mpicc
  openjdk
  python
  swig
)
if (( USE_R )) LIST+=r

# Run conda install!

set -x
if (( INSTALL_DEPS )) conda install --yes -c conda-forge $LIST
conda install --yes $PKG
