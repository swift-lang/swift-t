#!/bin/zsh
set -eu

# linux-64 CONDA PLATFORM
# Flags:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -R for the R version

C="" R=""
zparseopts -D -E C=C R=R

# Get this script path name (absolute):
SCRIPT=${0:A}
# Path to this directory
THIS=${SCRIPT:h}
# Tail name of this directory
export PLATFORM=${THIS:t}
# The Swift/T Conda script directory:
DEV_CONDA=${THIS:h}

cd $THIS
$DEV_CONDA/conda-build.sh $C $R
