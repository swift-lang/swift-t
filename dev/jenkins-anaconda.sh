#!/bin/bash
set -eu

# JENKINS ANACONDA SH
# Install Anaconda for GCE Jenkins
# Sets up 3 Minicondas: one in which to build   the package
#                   and one in which to install the package

renice --priority 19 --pid $$

# The Miniconda we are working with:
MINICONDA=Miniconda3-py39_23.3.1-0-Linux-x86_64.sh

# Clean up prior runs
rm -fv $MINICONDA
rm -fr $WORKSPACE/sfw/Miniconda-build
rm -fr $WORKSPACE/sfw/Miniconda-install
rm -fr /tmp/distro

(
  # Download and install both Minicondas:
  set -x
  wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
  bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-build
  bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-install
)

# Enable the build environment
PY=$WORKSPACE/sfw/Miniconda-build
PATH=$PY/bin:$PATH
source $PY/etc/profile.d/conda.sh
conda activate base
conda env list

task()
# Run a command line verbosely and report the time in simple format:
{
  echo task: ${*}
  /bin/time --format "time: %E" ${*}
}

git clone https://github.com/swift-lang/swift-t.git

# Create the "exported" Swift/T source tree in /tmp/distro
task swift-t/dev/release/make-release-pkg.sh
# Set up the build environment:
task swift-t/dev/conda/setup-conda.sh
# Build the Swift/T package:
task swift-t/dev/conda/linux-64/conda-platform.sh

# Enable the install environment
PY=$WORKSPACE/sfw/Miniconda-install
PATH=$PY/bin:$PATH
source $PY/etc/profile.d/conda.sh
conda activate base
conda env list

# PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
