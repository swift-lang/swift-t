#!/bin/bash
set -eu

# SETUP CONDA
# Install Anaconda build tools

which conda

conda env list

set -x
conda install --yes conda-build anaconda-client
