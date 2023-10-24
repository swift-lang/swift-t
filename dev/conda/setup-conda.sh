#!/bin/bash
set -eu

# SETUP CONDA
# Install Anaconda build tools

echo "setting up Anaconda build tools in:"
which conda
echo

conda env list

set -x
conda install --yes conda-build anaconda-client
