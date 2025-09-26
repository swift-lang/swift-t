#!/bin/zsh
set -eu

# CONDA INSTALL
# Install script for use by maintainers when testing PKGs.
# Normally you will want to use a different fresh Conda
#          from the Conda you used to build the PKG.
# Provide the PKG on the command line.
# NOTE: conda install from file does not install dependencies!
#       Cf. https://docs.anaconda.com/free/anaconda/packages/install-packages
#       Thus this script installs dependencies explicitly.
# NOTE: Keep LIST in sync with meta.yaml

help()
{
  cat <<EOF
NOTE:  This does some auto-configuration: check behavior
USAGE: Provide PKG
       Provide -C to configure (show dependencies) and exit
       Provide -D to skip installing dependencies
       Provide -P PLATFORM to change the CONDA_PLATFORM
               (else auto-detected from PKG directory)
               This is used when e.g. installing a PKG
               from a failed conda-build that is left in conda-bld/broken/
       Provide -r to install R
       Provide -s SOLVER to change the conda solver [classic,mamba]
EOF
}

# Parse the user options!
zparseopts -D -E h=H C=C D=D P:=P r=R s:=S

# Default behavior:
INSTALL_DEPS=1
USE_R=0
SOLVER=()

# Handle user flags:
if (( ${#H} )) { help ; return }
if (( ${#D} )) INSTALL_DEPS=0
if (( ${#R} )) USE_R=1
if (( ${#S} )) SOLVER=( --solver ${S[2]} )

if (( ${#*} != 1 )) abort "conda-install.sh: Provide PKG!"
PKG=$1

# Report information about given PKG:
print
print "CONDA INSTALL: PKG=$PKG"
# PKG is of form
# ANACONDA/conda-bld/PLATFORM/swift-t-V.V.V-pyVVV.tar.bz2
if (( ${#P} )) {
  CONDA_PLATFORM=${P[2]}
} else {
  # Pull out CONDA_PLATFORM directory (head then tail):
  CONDA_PLATFORM=${PKG:h:t}
}

# Bring in utilities
# Get this directory (absolute):
DEV_CONDA=${0:A:h}
# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}
source $SWIFT_T_TOP/turbine/code/scripts/helpers.zsh
source $DEV_CONDA/helpers.zsh

# Echo back platform and package statistics to the user
print "CONDA_PLATFORM=$CONDA_PLATFORM"
zmodload zsh/stat zsh/mathfunc
zstat -H A -F "%Y-%m-%d %H:%M" $PKG
printf "PKG: timestamp: %s size: %.1f MB\n" \
       ${A[mtime]} $(( float(${A[size]}) / (1024*1024) ))
printf "md5sum: "
# In DEV_CONDA/helpers.zsh:
checksum $PKG
print

# Report information about active Python/Conda:
if ! which conda >& /dev/null
then
  print "No conda!"
  return 1
fi

# Get 2nd token from the version string:
PV=( $( python --version ) )
PV=${PV[2]}

print "using python:" $( which python ) "(v$PV)"
print "using conda: " $( which conda )

# conda env list

# Defaults:
USE_ANT=1
USE_GCC=1
USE_ZSH=1

# Load platform-specific settings:
source $DEV_CONDA/$CONDA_PLATFORM/deps.sh

# Auto-configuration
# PINs are version pins applied to the package names
PIN_CLANG=""
PIN_MPICH=""
PIN_PV=""
if [[ $CONDA_PLATFORM == "osx-arm64" ]] {
  SOLVER=( --solver classic )
  # Pin Python version for these versions:
  # On GitHub, Conda will try to change them!
  case $PV {
    3.9*)  PIN_PV="==3.9.7" ;;
    3.10*)                  ;& # Fall-through
    3.11*)                  ;& # Fall-through
    3.12*)                  ;& # Fall-through
    3.13*) PIN_PV="==$PV"   ;;
    *)     print "conda-install.sh: unsupported Python version: '$PV'"
           exit 1 ;;
  }
  # Pin Clang for everything but Python 3.9
       # In Py 3.9, 18 => 18.1.7
  PIN_CLANG="-18==18.1.8"
  PIN_MPICH="==4.3.0"
}

# Build dependency list:
LIST=()
if (( USE_ANT )) LIST+=ant
if (( USE_GCC )) LIST+=gcc
if (( USE_ZSH )) LIST+=zsh
LIST+=(
  autoconf
  make
  "mpich-mpicc$PIN_MPICH"
  openjdk
  # May need to pin version:
  "python$PIN_PV"
  swig
)

# # Needed for _strstr issue:
if [[ $CONDA_PLATFORM == "osx-arm64" ]] LIST+=( "clang$PIN_CLANG" )

# Needed for libstdc++ GLIBCXX version issue on GCE Jenkins:
if [[ ${JENKINS_HOME:-} != "" ]] LIST+=( "gcc=14" )

# R switch
if (( USE_R )) {
  if [[ $CONDA_PLATFORM == "osx-arm64" ]] {
    LIST+="swift-t::emews-r"
  } else {
    # Use plain r on all other platforms:
    LIST+=r
  }
}

CONDA_FLAGS=( --yes --quiet $SOLVER )

if (( ${#C:-} )) {
  print "conda install: $CONDA_FLAGS $LIST"
  print "conda-install.sh: configure-only: exit"
  exit
}

# For set -x (includes newline):
PS4="
+ "

# Run conda install!
if (( INSTALL_DEPS )) {
     () {
       set -x
       conda install $CONDA_FLAGS -c conda-forge $LIST
     }
}


if [[ $CONDA_PLATFORM == "osx-arm64" ]] {
  SOLVER=( --solver classic )
  CONDA_FLAGS=( --yes --quiet $SOLVER )
}

set -x
conda install $CONDA_FLAGS $PKG
