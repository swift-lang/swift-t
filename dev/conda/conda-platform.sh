#!/bin/zsh
set -eu

# CONDA PLATFORM
# Build conda for a given PLATFORM
# Dependency files are in the PLATFORM directory
# Generated scripts and log files are put in the PLATFORM directory
# Arguments:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -r R_VERSION for the R version
#  PLATFORM: The PLATFORM directory

C="" R=""
zparseopts -D -E -F h=HELP C=C r:=R
if (( ${#*} != 1 )) {
  print "conda-platform.sh: Provide PLATFORM!"
  return 1
}
export PLATFORM=$1

# The Swift/T Conda script directory (absolute):
DEV_CONDA=${0:A:h}

if [[ ! -d $DEV_CONDA/$PLATFORM ]] {
  printf "conda-platform.sh: No such platform: '%s'\n" $PLATFORM
  return 1
}

cd $DEV_CONDA/$PLATFORM
$DEV_CONDA/conda-build.sh $HELP $C $R
