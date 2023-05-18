#!/bin/bash

# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins

set -eu

renice --priority 19 --pid $$

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

PY=$WORKSPACE/sfw/Miniconda-build
PATH=$PY/bin:$PATH

source $PY/etc/profile.d/conda.sh
conda activate base
conda env list

task()
{
  echo task: ${*}
  /bin/time --format "time: %E" ${*}
}

task swift-t/dev/release/make-release-pkg.zsh

task swift-t/dev/conda/setup-conda.sh
task swift-t/dev/conda/linux-64/conda-platform.sh

PY=$WORKSPACE/sfw/Miniconda-install
PATH=$PY/bin:$PATH
source $PY/etc/profile.d/conda.sh
conda activate base
conda env list

# PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
