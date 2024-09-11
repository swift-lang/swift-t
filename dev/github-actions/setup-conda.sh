#!/bin/bash
set -eu

# SETUP CONDA
# Sets up system tools for an Anaconda Swift/T build
# For any matrix.os

MATRIX_OS=$1

START=$SECONDS

log()
{
  printf "setup-conda: %s\n" "$*"
}

log "Installing dependencies for OS=$MATRIX_OS ..."

# Set up tools:
case $MATRIX_OS in
  "ubuntu-latest")
    TOOL=( sudo apt-get install --yes )
    ;;
  macos-*)
    TOOL=( brew install )
    brew update >& tool.log
    ;;
  *)
    log "unknown OS: $MATRIX_OS"
    exit 1
esac

# Basic Mac packages:
PKGS_MAC=(
  autoconf
  automake
  coreutils
  # To resolve the sed -i problem on Mac
  gnu-sed
  # For consistent timing messages:
  gnu-time
)

# Select package lists:
case $MATRIX_OS in
  "ubuntu-latest")
    PKGS=( zsh )
    ;;
  "macos-13")
    PKGS=( ${PKGS_MAC[@]} )
    ;;
  "macos-14")
    PKGS=( ${PKGS_MAC[@]} )
    ;;
  "macos-14-arm64")
    PKGS=( ${PKGS_MAC[@]} )
    ;;
  *)
    log "unknown OS: $MATRIX_OS"
    exit 1
esac

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
    /opt/homebrew/opt/coreutils/libexec/gnubin
    /opt/homebrew/opt/gnu-sed/libexec/gnubin
    /opt/homebrew/opt/gnu-time/libexec/gnubin
    /opt/homebrew/opt/bin
  )
  echo ${BINS[@]} | fmt -w 1 >> $GITHUB_PATH
fi

log SUCCESS >> tool.log
