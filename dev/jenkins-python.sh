#!/bin/zsh
set -eu

# Jenkins Python
# Install Anaconda for GCE Jenkins

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

$WORKSPACE/sfw/Miniconda/bin/python -c 'print("Python works.")'
