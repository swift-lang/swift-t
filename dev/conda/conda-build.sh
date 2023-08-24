#!/bin/zsh
set -eu

# CONDA BUILD
# Generic wrapper around `conda build'
# Called by platform/conda-platform.sh
# Generates meta.yaml and runs `conda build'
# This script runs in the PLATFORM subdirectory
#      and should not change directories
# A LOG is produced named platform/conda-build.log
# Flags:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -R for the R version

if (( ${#PLATFORM:-} == 0 )) {
  print "conda-build.sh: unset: PLATFORM"
  print "                This script should be called by"
  print "                a conda-platform.sh"
  return 1
}

print "PLATFORM: $PLATFORM"

# Get this directory (absolute):
DEV_CONDA=${0:A:h}
# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}

C="" R=""
zparseopts -D -E C=C R=R

if [[ ! -d /tmp/distro ]] {
  print "conda-build.sh: Swift/T source not found at: /tmp/distro"
  return 1
}

# Check that the conda-build tool in use is in the
#       selected Python installation
if ! which conda-build >& /dev/null
then
  print "conda-build.sh: could not find tool: conda-build"
  print "                run ./setup-conda.sh"
  return 1
fi
# Look up executable:
CONDA_BUILD_TOOL=( =conda-build )
# Get its directory:
TOOLDIR=${CONDA_BUILD_TOOL:h}
# Look up executable:
PYTHON_EXE=( =python )
# Get its directory:
PYTHON_BIN=${PYTHON_EXE:h}
if [[ ${TOOLDIR} != ${PYTHON_BIN} ]] {
  print "conda-build.sh: conda-build is not in your python directory!"
  print "                this is probably wrong!"
  print "                run ./setup-conda.sh"
  return 1
}

COMMON_M4=common.m4
META_TEMPLATE=$DEV_CONDA/meta-template.yaml
SETTINGS_SED=$DEV_CONDA/settings.sed

if (( ! ${#R} )) {
  export PKG_NAME="swift-t"
} else {
  export ENABLE_R=1
  export PKG_NAME="swift-t-r"
}

# Default dependencies:
export USE_ANT=1
export USE_GCC=1
export USE_ZSH=1

# Check last entry in PLATFORM path:
if [[ ${PLATFORM:t} == "osx-arm64" ]] {
  # For OSX/Arm64, we rely on these in the system:
  USE_ANT=0
  USE_GCC=0
  USE_ZSH=0
}
if [[ ${PLATFORM:t} == "osx-64" ]] {
  # For OSX/Intel, we rely on Clang:
  USE_GCC=0
}

m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

if (( ${#C} )) {
  print "conda-build.sh: configure-only: exit."
  exit
}

# Backup the old log
LOG=conda-build.log
if [[ -f $LOG ]] {
  mv -v $LOG $LOG.bak
  print
}

{
  DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"
  print "CONDA BUILD: START: ${(%)DATE_FMT_S}"
  (
    print "using python, conda:"
    which python conda
    conda env list
    print

    set -x
    # This purge-all is extremely important:
    conda build purge-all

    # Build the package!
    conda build \
          -c conda-forge \
          --dirty \
          .
  )
  print "CONDA BUILD: STOP: ${(%)DATE_FMT_S}"
} |& tee $LOG
print
print "conda build succeeded."
print

# Find the "upload" text for the PKG in the LOG,
#      this will give us the PKG file name
UPLOAD=( $( grep -A 1 "anaconda upload" $LOG ) )
PKG=${UPLOAD[-1]}

if [[ $PLATFORM =~ osx-* ]] {
  MD5=( md5 -r )
} else {
  MD5=( md5sum )
}

# Print metadata about the PKG
(
  print
  zmodload zsh/stat
  zstat -H A -F "%Y-%m-%d %H:%M" $PKG
  print ${A[mtime]} ${A[size]} $PKG
  printf "md5sum: "
  $MD5 $PKG
) | tee -a $LOG
print
