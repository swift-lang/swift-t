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

CONDA_EXE=$(which conda)

# For 'set -x' , including newline
PS4="
+ "

set -x
echo CONDA_EXE $CONDA_EXE
CONDA_HOME=$(dirname $(dirname $CONDA_EXE))
CONDA_BIN_DIR=$CONDA_HOME/bin
set +x
echo source $CONDA_BIN_DIR/activate
source $CONDA_BIN_DIR/activate
set -x

which python conda

() {
  set -x
  conda install --yes --quiet -c conda-forge -c swift-t $PKG
  rehash
  echo $PATH

  which Rscript
  Rscript dev/conda/install-RInside.R
}

log OK
