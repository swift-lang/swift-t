#!/bin/zsh
set -eu

# CONDA BUILD
# Generic interface to `conda build'
# Called by platform/conda-platform.sh
# Generates meta.yaml and runs `conda build'
# This script runs in the PLATFORM subdirectory
#      and should not change directories

# Get this directory (absolute):
DEV_CONDA=${0:A:h}
# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}

R=""
zparseopts -D -E R=R

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

COMMON_M4=$SWIFT_T_TOP/turbine/code/scripts/common.m4
META_TEMPLATE=$DEV_CONDA/meta-template.yaml
SETTINGS_SED=$DEV_CONDA/settings.sed

if (( ! ${#R} )) {
  export PKG_NAME="swift-t"
} else {
  export ENABLE_R=1
  export PKG_NAME="swift-t-r"
}

m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

# Backup the old log
LOG=conda-build.log
if [[ -f $LOG ]]
then
  mv -v --backup=numbered $LOG $LOG.bak
  echo
fi

{
  DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"
  print "CONDA BUILD: START: ${(%)DATE_FMT_S}"
  (
    echo "using python, conda:"
    which python conda
    conda env list
    echo

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
echo
echo "conda build succeeded."
echo

# Find the "upload" text for the PKG in the LOG,
UPLOAD=( $( grep -A 1 "anaconda upload" $LOG ) )
FILE=${UPLOAD[-1]}

# Capture the checksum for later
(
  echo
  echo md5sum: $( md5sum $FILE )
) | tee --append $LOG
echo
