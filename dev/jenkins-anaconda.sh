#!/bin/bash

# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins

set -eu

renice --priority 19 --pid $$

pwd
ls

MINICONDA=Miniconda3-py39_23.3.1-0-Linux-x86_64.sh

rm -fv $MINICONDA
rm -fr $WORKSPACE/sfw/Miniconda-build
rm -fr $WORKSPACE/sfw/Miniconda-install


(
  set -x
  wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
  bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-build
  bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-install
)

PATH=$WORKSPACE/sfw/Miniconda-build/bin:$PATH

set +x
source "$WORKSPACE/sfw/Miniconda-build/etc/profile.d/conda.sh"
conda activate base

swift-t/dev/conda/setup-conda.sh

# PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
