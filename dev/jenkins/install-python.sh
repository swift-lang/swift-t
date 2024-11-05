#!/bin/bash
set -eu

# JENKINS INSTALL PYTHON
# Install Miniconda for CELS Jenkins

renice --priority 19 --pid $$

# The Miniconda we are working with:
MINICONDA=Miniconda3-py39_23.3.1-0-Linux-x86_64.sh

# Clean up prior runs
rm -fv $MINICONDA
rm -fr $WORKSPACE/sfw/Miniconda

(
  # Download and install Miniconda:
  set -x
  wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
  bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda
)

PATH=$WORKSPACE/sfw/Miniconda/bin:$PATH

set -x
which python
python -c 'print("Python works.")'

# Needed for javac
conda install openjdk
