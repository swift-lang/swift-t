#!/bin/zsh
set -eu

# CONDA BUILD
# Generic wrapper around `conda build'
# Called by platform/conda-platform.sh
# Generates meta.yaml and runs `conda build'
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

DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
# This has nothing to do with log file $LOG
{
  print ${(%)DATE_FMT_S} "conda-build.sh:" ${*}
}

if (( ${#PLATFORM:-} == 0 )) {
  log "unset: PLATFORM"
  log "       This script should be called by a conda-platform.sh"
  return 1
}

C="" R=""
zparseopts -D -E -F h=HELP C=C r=R

if (( ${#HELP} )) {
  help
  exit
}

log "PLATFORM: $PLATFORM $*"

# Get this directory (absolute):
DEV_CONDA=${0:A:h}
# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}
TMP=${TMP:-/tmp}

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

# Allow platform to modify dependencies
source $DEV_CONDA/$PLATFORM/deps.sh

export DATE=${(%)DATE_FMT_S}
m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

if (( ${#C} )) {
  log "configure-only: exit."
  exit
}

# Backup the old log
LOG=conda-build.log
log "LOG: $LOG"
if [[ -f $LOG ]] {
  mv -v $LOG $LOG.bak
  print
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
log "looking for upload line in $LOG ..."
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
  log ${A[mtime]} ${A[size]} $PKG
  log "md5sum:" $( $MD5 $PKG )
) | tee -a $LOG
print
