#!/bin/zsh
set -eu
exec 2>&1

# INSTALL R SH
# and RInside

MATRIX_OS=$1

START=$SECONDS

log()
{
  printf "install-R.sh: %s\n" "$*"
}

case $MATRIX_OS {
  "ubuntu-latest") PKG="r"       ;;
  "macos-14")      PKG="emews-r" ;;
  *) log "unknown OS=$MATRIX_OS"
     exit 1 ;;
}

# For 'set -x' , including newline
PS4="
+ "

# The locations from the GitHub builtin setup-miniconda
# The installation is a bit different on GitHub
# conda    is in $CONDA_HOME/condabin
# activate is in $CONDA_HOME/bin

CONDA_EXE=$(which conda)

echo "CONDA_EXE:   $CONDA_EXE"
CONDA_HOME=$( dirname $( dirname $CONDA_EXE ) )
echo "CONDA_HOME:  $CONDA_HOME"
CONDA_BIN_DIR=$CONDA_HOME/bin
# Cannot leave env name blank on GitHub?
ACTIVATE=( source $CONDA_BIN_DIR/activate base )
log $ACTIVATE
$ACTIVATE
log "activated."
log "CONDA_PREFIX: $CONDA_PREFIX"

() {
  set -x
  # Use =conda to prevent conda() and 'set -eux'
  =conda install --yes --quiet -c conda-forge -c swift-t $PKG
  rehash

  which Rscript
  Rscript dev/conda/install-RInside.R
}

log OK
