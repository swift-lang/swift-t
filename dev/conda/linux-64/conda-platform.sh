#!/bin/zsh
set -eu

# linux-64 CONDA PLATFORM
# Provide -R for the R version

R=""
zparseopts -D -E R=R

# Get this directory (absolute):
PLATFORM=${0:A:h}
# The Swift/T Conda script directory:
DEV_CONDA=${PLATFORM:h}

$DEV_CONDA/conda-build.sh $R
