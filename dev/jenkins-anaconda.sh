#!/bin/zsh
set -eu

# JENKINS ANACONDA SH
# Test the Swift/T Anaconda packages
# Sets up 2 Minicondas: one in which to build   the package
#                   and one in which to install the package

UNINSTALL=""
zparseopts u=UNINSTALL

renice --priority 19 --pid $$

setopt PUSHD_SILENT

# The Miniconda we are working with:
MINICONDA=Miniconda3-py39_23.3.1-0-Linux-x86_64.sh

# Clean up prior runs
if (( UNINSTALL )) {
  rm -fv $MINICONDA
  rm -fr $WORKSPACE/sfw/Miniconda-build
  rm -fr $WORKSPACE/sfw/Miniconda-install
  rm -fr swift-t
  rm -fr /tmp/distro
}

(
  # Download and install both Minicondas:
  set -x
  if [[ ! -f $MINICONDA ]] \
       wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
  for LABEL in build install
  do
    if [[ ! -f sfw/Miniconda-$LABEL ]] \
         bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-$LABEL
  done
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

if [[ -d swift-t ]]
then
  cd swift-t
  git checkout master
  git pull
  cd -
else
  git clone https://github.com/swift-lang/swift-t.git
fi

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
