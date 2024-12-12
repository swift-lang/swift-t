#!/bin/zsh
set -eu

# CONDA PLATFORM
# Build conda for a given CONDA_PLATFORM
# Dependency files are in the CONDA_PLATFORM directory
# Generated scripts and logs are put in the CONDA_PLATFORM directory
# Arguments:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -r R_VERSION for the R version
#  CONDA_PLATFORM: The PLATFORM directory

C="" R=""
zparseopts -D -E -F h=HELP C=C r:=R
if (( ${#*} != 1 )) {
  print "conda-platform.sh: Provide CONDA_PLATFORM!"
  return 1
}
# The PLATFORM under Anaconda naming conventions:
export CONDA_PLATFORM=$1

# The Swift/T Conda script directory (absolute):
DEV_CONDA=${0:A:h}

if [[ ! -d $DEV_CONDA/$CONDA_PLATFORM ]] {
  printf "conda-platform.sh: No such platform: '%s'\n" $CONDA_PLATFORM
  return 1
}

cd $DEV_CONDA/$CONDA_PLATFORM
$DEV_CONDA/conda-build.sh $HELP $C $R
