#!/bin/bash

# SETUP for matrix.os == macos-14

START=$SECONDS

log()
{
  printf "setup-macos-14.sh: %s\n" "$*"
}

log "Installing Homebrew packages..."

PKGS=(
  autoconf
  automake
  # To resolve the sed -i problem on Mac
  gnu-sed
  # java
  make
  mpich
  swig
  tcl-tk
)

if (
  set -eux
  brew update
  brew install ${PKGS[@]}
) # >& brew.log
then
  T=$(( SECONDS - START ))
  log "Installed Homebrew packages in $T seconds."
else
  log "FAILED to install Homebrew packages!"
  log "brew.log:"
  cat brew.log
  exit 1
fi

echo /opt/homebrew/opt/make/libexec/gnubin >> $GITHUB_PATH
echo /opt/homebrew/opt/bin                 >> $GITHUB_PATH
