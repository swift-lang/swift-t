#!/bin/zsh
set -eu

# linux-64 CONDA PLATFORM
# Flags:
#  -C configure-only- generate meta.yaml and settings.sed, then stop
#  -r for the R version

C="" R=""
zparseopts -D -E h=HELP C=C r=R

# Get this script path name (absolute):
SCRIPT=${0:A}
# Path to this directory
THIS=${SCRIPT:h}
# Tail name of this directory
export PLATFORM=${THIS:t}
# The Swift/T Conda script directory:
DEV_CONDA=${THIS:h}

# Sets PYTHON_VERSION
source $DEV_CONDA/get-python-version.sh

cd $THIS
$DEV_CONDA/conda-build.sh $HELP $C $R
