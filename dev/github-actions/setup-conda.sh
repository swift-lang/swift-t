#!/bin/bash
set -eu

# SETUP CONDA
# Sets up system tools for an Anaconda Swift/T build
# For any matrix.os
# Produces artifact tool.log, which is checked by subsequent steps

MATRIX_OS=$1

START=$SECONDS

log()
{
  printf "setup-conda: %s\n" "$*"
}

log "Installing dependencies for OS=$MATRIX_OS ..."
# Create initial timestamp:
log > tool.log

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
    # macos-13 already has autoconf, automake
    PKGS=( ${PKGS_MAC[@]} )
    ;;
  "macos-14")
    PKGS=( ${PKGS_MAC[@]}
           autoconf
           automake
         )
    ;;
  "macos-14-arm64")
    PKGS=( ${PKGS_MAC[@]}
           autoconf
           automake
         )
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
case $MATRIX_OS in
  macos-13)
    BINS=(
      /usr/local/opt/coreutils/libexec/gnubin
      /usr/local/opt/gnu-sed/libexec/gnubin
      /usr/local/opt/gnu-time/libexec/gnubin
    )
    ;;
  macos-14*)
    BINS=(
      /opt/homebrew/opt/coreutils/libexec/gnubin
      /opt/homebrew/opt/gnu-sed/libexec/gnubin
      /opt/homebrew/opt/gnu-time/libexec/gnubin
      /opt/homebrew/opt/bin
    )
    ;;
esac

echo ${BINS[@]} | fmt -w 1 >> $GITHUB_PATH
{
  echo PATHS:
  echo ${BINS[@]} | fmt -w 1
} >> tool.log

log "SUCCESS" >> tool.log
