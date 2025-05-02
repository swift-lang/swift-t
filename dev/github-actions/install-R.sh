#!/bin/zsh
set -eu

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

() {
  set -x
  conda install --yes --quiet -c conda-forge -c swift-t $PKG
  rehash
  echo $PATH
  which python
  which Rscript
  Rscript dev/conda/install-RInside.R
}

log OK
