#!/bin/zsh
set -eu

# osx-64 (Intel) CONDA PLATFORM
# Flags:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -r for the R version

HELP="" C="" R=""
zparseopts -D -E -F h=HELP C=C r=R

# Get this script path name (absolute):
SCRIPT=${0:A}
# Path to this directory
THIS=${SCRIPT:h}
# Tail name of this directory
export PLATFORM=${THIS:t}
# The Swift/T Conda script directory:
DEV_CONDA=${THIS:h}

cd $THIS
$DEV_CONDA/conda-build.sh $HELP $C $R
