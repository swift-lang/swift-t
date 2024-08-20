#!/bin/zsh
set -eu

# CONDA BUILD
# Generic wrapper around `conda build'
# Called by platform/conda-platform.sh
# Generates meta.yaml and runs `conda build'
# Generates settings.sed for the Swift/T build
# Many exported environment variables here
#      are substituted into meta.yaml
# This script runs in the PLATFORM subdirectory
#      and should not change directories
# A LOG is produced named platform/conda-build.log
# You can only run 1 job concurrently
#     because of the log and
#     because of meta.yaml
# The Swift/T source must have already been put in $TMP/distro

help()
{
  cat <<END

Options:
   -C configure-only- generate meta.yaml and settings.sed, then stop
   -R for the R version

END
}

C="" R=""
zparseopts -D -E -F h=HELP C=C r=R

if (( ${#HELP} )) {
  help
  exit
}

# Get this directory (absolute):
DEV_CONDA=${0:A:h}

# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}
TMP=${TMP:-/tmp}

source $SWIFT_T_TOP/turbine/code/scripts/helpers.zsh
# Sets SWIFT_T_VERSION:
source $SWIFT_T_TOP/dev/get-versions.sh
source $DEV_CONDA/helpers.zsh
# Sets PYTHON_VERSION:
source $DEV_CONDA/get-python-version.sh

export SWIFT_T_VERSION

if (( ${#PLATFORM:-} == 0 )) {
  log "unset: PLATFORM"
  log "       This script should be called by a conda-platform.sh"
  return 1
}

log "VERSION:  $SWIFT_T_VERSION"
log "PLATFORM: $PLATFORM $*"

# This is passed into meta.yaml:
export DISTRO=$TMP/distro
if [[ ! -d $DISTRO ]] {
  log "Swift/T source not found at: $DISTRO"
  return 1
}

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
  return 1
}

# We must set CONDA_PREFIX:
# https://github.com/ContinuumIO/anaconda-issues/issues/10156
export CONDA_PREFIX=${PYTHON_BIN:h}

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

# Default dependencies:
export USE_ANT=1
export USE_GCC=1
export USE_TK=0
export USE_ZSH=1

# Allow platform to modify dependencies
source $DEV_CONDA/$PLATFORM/deps.sh

export DATE=${(%)DATE_FMT_S}
m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

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

if (( ENABLE_R )) && [[ $PLATFORM == "osx-arm64" ]] {
  # This is just for our emews-rinside:
  CHANNEL_SWIFT=( -c swift-t )
} else {
  CHANNEL_SWIFT=()
}

{
  log "CONDA BUILD: START: ${(%)DATE_FMT_S}"
  print
  (
    log "using python: " $( which python )
    log "using conda:  " $( which conda  )
    print
    conda env list
    print

    set -x
    # This purge-all is extremely important:
    conda build purge-all

    # Build the package!
    conda build \
          -c conda-forge \
          $CHANNEL_SWIFT \
          --dirty \
          .
  )
  log "CONDA BUILD: STOP: ${(%)DATE_FMT_S}"
} |& tee $LOG
print
log "conda build succeeded."
print

# Find the "upload" text for the PKG in the LOG,
#      this will give us the PKG file name
log "looking for upload line in ${LOG:a} ..."
UPLOAD=( $( grep -A 1 "anaconda upload" $LOG ) )
PKG=${UPLOAD[-1]}

# Print metadata about the PKG
(
  print
  zmodload zsh/mathfunc zsh/stat
  print PKG=$PKG
  zstat -H A -F "%Y-%m-%d %H:%M" $PKG
  log  "TIME: ${A[mtime]} ${A[size]}"
  printf -v T "SIZE: %.1f MB" $(( float(${A[size]}) / (1024*1024) ))
  log $T
  log "HASH:" $( checksum $PKG )
) | tee -a $LOG
print
