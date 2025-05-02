#!/bin/bash
set -eu

# SETUP DEPS
# Sets up system tools for a full Swift/T build
# For any matrix.os

MATRIX_OS=$1

START=$SECONDS

log()
{
  printf "setup-deps: %s\n" "$*"
}

apt list --installed

log "Installing dependencies for OS=$MATRIX_OS ..."

if [[ $MATRIX_OS == "ubuntu-latest" ]]
then
  TOOL=( sudo apt-get install --yes )
  PKGS=(
    autoconf
    default-jdk
    libcurl4-openssl-dev
    make
    mpich
    tcl-dev
    zsh
  )
elif [[ $MATRIX_OS == "macos-14" ]]
then
  TOOL=( brew install )
  PKGS=(
    autoconf
    automake
    # To resolve the sed -i problem on Mac
    gnu-sed
    make
    mpich
    swig
    tcl-tk
  )
  brew update >& tool.log
elif [[ $MATRIX_OS == "macos-14-arm64" ]]
then
  TOOL=( brew install )
  PKGS=(
    autoconf
    automake
    # To resolve the sed -i problem on Mac
    gnu-sed
    make
    mpich
    swig
    tcl-tk
  )
  brew update >& tool.log
else
  log "unknown OS: $MATRIX_OS"
  exit 1
fi

if (
  set -eux
  ${TOOL[@]} ${PKGS[@]}
) 2>&1 >> tool.log
then
  COUNT=${#PKGS[@]}
  T=$(( SECONDS - START ))
  log "Installed $COUNT packages in $T seconds."
else
  log "FAILED to install packages!"
  log "tool.log:"
  cat  tool.log
  exit 1
fi

# Setup Mac PATH
# Add these tools to PATH via GITHUB_PATH, one per line
if [[ $MATRIX_OS == macos-* ]]
then
  BINS=(
    /opt/homebrew/opt/gnu-sed/libexec/gnubin
    /opt/homebrew/opt/make/libexec/gnubin
    /opt/homebrew/opt/bin
  )
  echo ${BINS[@]} | fmt -w 1 >> $GITHUB_PATH
fi

apt list --installed
