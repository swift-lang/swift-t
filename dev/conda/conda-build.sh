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

COMMON_M4=$SWIFT_T_TOP/turbine/code/scripts/common.m4
META_TEMPLATE=$DEV_CONDA/meta-template.yaml
SETTINGS_SED=$DEV_CONDA/settings.sed

if (( ${#R} )) export ENABLE_R=1

m4 -P -I $DEV_CONDA $COMMON_M4 $META_TEMPLATE > meta.yaml
m4 -P -I $DEV_CONDA $COMMON_M4 $SETTINGS_SED  > settings.sed

exit

# Backup the old log
LOG=conda-build.log
if [[ -f $LOG ]]
then
  mv -v --backup=numbered $LOG $LOG.bak
  echo
fi

(
  DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"
  print "CONDA BUILD: START: ${(%)DATE_FMT_S}"

  echo "using conda:"
  which conda
  echo

  set -x
  # This purge-all is extremely important:
  conda build purge-all

  # Build the package!
  conda build \
        -c conda-forge \
        --dirty \
        .

  print "CONDA BUILD: STOP: ${(%)DATE_FMT_S}"
) |& tee $LOG
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



# --debug

#      --no-remove-work-dir

# --bootstrap NAME

# --keep-old-work       .
  #

# --skip-existing

# -c "bioconda/label/cf201901"

# --output # <-- This skips the build and just does a print!
