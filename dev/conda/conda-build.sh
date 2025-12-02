#!/bin/zsh
set -eu
setopt PIPE_FAIL

# CONDA BUILD
# Generic wrapper around `conda build'
# Generates meta.yaml and runs `conda build'
# Generates settings.sed to edit swift-t-settings.sh
# Many exported environment variables here
#      are substituted into meta.yaml
# This script runs in the CONDA_PLATFORM subdirectory
#      and should not change directories
# A LOG is produced named CONDA_PLATFORM/conda-build.log
# You can only run 1 job concurrently per Anaconda installation
#     because of the log and
#     because of meta.yaml
# The Swift/T source must have already been put in $TMP/distro
#     via Swift/T dev/release/make-release-pkg.zsh

help()
{
  cat <<END

Options:
   conda-build.sh [-Cv] [-r RV] PLATFORM
   -C    : configure-only-
           generate meta.yaml and settings.sed, then stop
   -r RV : enable R version RV
   -v    : verbose (show meta.yaml and settings.sed)

END
  exit
}

C="" R="" R_VERSION=""
zparseopts -D -E -F h=HELP C=C r:=R v=VERBOSE

if (( ${#HELP} )) help
if (( ${#*} != 1 )) abort "conda-build.sh: Provide CONDA_PLATFORM!"

# The PLATFORM under Anaconda naming conventions:
export CONDA_PLATFORM=$1

# The Swift/T Conda script directory (absolute):
DEV_CONDA=${0:A:h}

# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}
TMP=${TMP:-/tmp}

source $DEV_CONDA/helpers.zsh
source $SWIFT_T_TOP/turbine/code/scripts/helpers.zsh

# For log():
LOG_LABEL="conda-build.sh:"

log "CONDA_PLATFORM:  $CONDA_PLATFORM ${*}"

# Sets SWIFT_T_VERSION:
source $SWIFT_T_TOP/dev/get-versions.sh
export SWIFT_T_VERSION
log "SWIFT/T VERSION: $SWIFT_T_VERSION"
# Setenvs PYTHON_VERSION: passed into meta.yaml: e.g. "3.12.11"
source $DEV_CONDA/get-python-version.sh
log "PYTHON_VERSION: $PYTHON_VERSION   SERIES: $PYTHON_SERIES"
# Optionally set R_VERSION from user argument:
if (( ${#R} )) export R_VERSION=${R[2]}

if [[ ! -d $DEV_CONDA/$CONDA_PLATFORM ]] \
  abortf "conda-build.sh: No such platform: '%s'\n" $CONDA_PLATFORM

cd $DEV_CONDA/$CONDA_PLATFORM

# This is passed into meta.yaml:
export DISTRO=$TMP/distro
if [[ ! -d $DISTRO ]] abort "Swift/T source not found at: $DISTRO"

# Check that the conda-build tool in use is in the
#       selected Python installation
if ! which conda-build >& /dev/null
then
  log "could not find tool: conda-build"
  log "                     run ./setup-conda.sh"
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
  log "conda-build is not in your python directory!"
  log "            this is probably wrong!"
  log "            run ./setup-conda.sh"
  print
  log "found:"
  log "conda-build in $TOOLDIR"
  log "python      in $PYTHON_BIN"
  return 1
}

# We must set CONDA_PREFIX:
# https://github.com/ContinuumIO/anaconda-issues/issues/10156
export CONDA_PREFIX=${PYTHON_BIN:h}
log "CONDA_PREFIX: $CONDA_PREFIX"

COMMON_M4=common.m4
META_TEMPLATE=$DEV_CONDA/meta-template.yaml
SETTINGS_SED=$DEV_CONDA/settings.sed

if (( ! ${#R} )) {
  export ENABLE_R=0
  export PKG_NAME="swift-t"
} else {
  export ENABLE_R=1
  export PKG_NAME="swift-t-r"
}
log "PKG_NAME: $PKG_NAME"

# Default dependencies:
export USE_ANT=1
export USE_CLANG=0
export USE_GCC=1
export USE_NCURSES=0
export USE_TK=0
export USE_ZLIB=0
export USE_ZSH=1
export SPEC_MPICH="mpich-mpicc"
export SPEC_CLANG=""

# Allow platform to modify dependencies
source $DEV_CONDA/$CONDA_PLATFORM/deps.sh

# This is just for automated testing
# Provide a default value for ease of debugging:
export GITHUB_ACTIONS=${GITHUB_ACTIONS:-false}

# Export a DATE for provenance in m4-filtered files:
export DATE=${(%)DATE_FMT_S}
# Report with relative directories:
log "writing ${PWD#${SWIFT_T_TOP}/}/meta.yaml"
m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
log "writing ${PWD#${SWIFT_T_TOP}/}/settings.sed"
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

if (( ${#VERBOSE} )) {
  echo
  log "meta.yaml:"
  cat meta.yaml
  echo
  log "settings.sed:"
  cat settings.sed
  echo
}

if (( ${#C} )) {
  log "configure-only: exit."
  return
}

# Backup the old log
LOG=conda-build.log
log "LOG: $LOG"
if [[ -f $LOG ]] {
  mv -v $LOG $LOG.bak
  print
}

if (( ENABLE_R )) && [[ $CONDA_PLATFORM == "osx-arm64" ]] {
  # This is just for our emews-r:
  CHANNEL_SWIFT=( -c swift-t )
} else {
  CHANNEL_SWIFT=()
}

# Disable
# "UserWarning: The environment variable 'X' is being passed through"
export PYTHONWARNINGS="ignore::UserWarning"

{
  log "BUILD: START"
  print
  (
    log "using python: " $( which python )
    log "using conda:  " $( which conda  )
    print

    BUILD_ARGS=( -c conda-forge
                 -c defaults
                 --dirty
                 $CHANNEL_SWIFT
                 .
               )

    log "conda build: purge-all ..."
    # This purge-all is extremely important:
    conda build purge-all

    log "conda build: $BUILD_ARGS ..."

    # Build the package!
    conda build $BUILD_ARGS
  )
  log "BUILD: STOP"
} |& tee $LOG
print

# Find the "upload" text for the PKG in the LOG,
#      this will give us the PKG file name
PKG=""
if UPLOAD=( $( grep -A 1 "anaconda upload" $LOG ) )
then
  log "UPLOAD: ${UPLOAD:-EMPTY}"
  PKG=${UPLOAD[-1]}
  log "found PKG=${PKG:-NOT_FOUND}"
fi

if [[ $PKG == "" ]] \
  abort "could not find 'anaconda upload' in the log!"

# Print metadata about the PKG
(
  print
  zmodload zsh/mathfunc zsh/stat
  print PKG=$PKG
  zstat -H A -F "%Y-%m-%d %H:%M" $PKG
  log  "TIME: ${A[mtime]}"
  printf -v T "SIZE: %.1f MB" $(( float(${A[size]}) / (1024*1024) ))
  log $T
  log "HASH:" $( checksum $PKG )
) | tee -a $LOG
print
