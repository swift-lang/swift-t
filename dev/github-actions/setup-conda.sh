#!/bin/bash
set -eu

# SETUP CONDA
# Sets up system tools for an Anaconda Swift/T build
# For any matrix.os
# Produces artifact setup-conda.log, which is checked by subsequent steps

MATRIX_OS=$1

START=$SECONDS

echo "dev/github-actions/setup-conda.sh: START" \
     $(date "+%Y-%m-%d %H:%M")                  \
     >> setup-conda.log

log()
{
  printf "setup-conda: %s\n" "$*"
}

log "Installing dependencies for OS=$MATRIX_OS ..."
# Create initial timestamp:
log "Start..." >> setup-conda.log

# Set up tools:
case $MATRIX_OS in
  "ubuntu-latest")
    log "apt install..." >> setup-conda.log
    TOOL=( sudo apt-get install --yes )
    ;;
  macos-*)
    TOOL=( brew install )
    log "brew update..." >> setup-conda.log
    brew update 2>&1     >> setup-conda.log
    ;;
  *)
    log "unknown OS: $MATRIX_OS"
    exit 1
esac

# Basic Mac packages:
PKGS_MAC=(
  automake
  make
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
    # macos-13 already has autoconf
    PKGS=( ${PKGS_MAC[@]} )
    ;;
  "macos-14")
    PKGS=( ${PKGS_MAC[@]}
           autoconf
         )
    ;;
  "macos-14-arm64")
    PKGS=( ${PKGS_MAC[@]}
           autoconf
         )
    ;;
  *)
    log "unknown OS: $MATRIX_OS"
    exit 1
esac

if (
  set -eux
  ${TOOL[@]} ${PKGS[@]}
) 2>&1 >> setup-conda.log
then
  COUNT=${#PKGS[@]}
  T=$(( SECONDS - START ))
  log "Installed $COUNT packages in $T seconds."
else
  log "FAILED to install packages!"
  log "setup-conda.log:"
  cat  setup-conda.log
  exit 1
fi

# Setup Mac PATH
# Add these tools to PATH via GITHUB_PATH, one per line
case $MATRIX_OS in
  macos-13)
    BINS=(
      /usr/local/opt/make/bin
      /usr/local/opt/coreutils/libexec/gnubin
      /usr/local/opt/gnu-sed/libexec/gnubin
      /usr/local/opt/gnu-time/libexec/gnubin
      # Does not exist: /usr/local/opt/bin
    )
    ;;
  macos-14*)
    BINS=(
      # Should be in main bin:
      # /opt/homebrew/opt/autoconf/libexec/gnubin
      # /opt/homebrew/opt/automake/libexec/gnubin
      /opt/homebrew/opt/make/libexec/gnubin
      /opt/homebrew/opt/coreutils/libexec/gnubin
      /opt/homebrew/opt/gnu-sed/libexec/gnubin
      /opt/homebrew/opt/gnu-time/libexec/gnubin
      /opt/homebrew/bin
    )
    ;;
esac

for BIN in ${BINS[@]}
do
  if [[ ! -d ${BIN} ]]
  then
    echo "BIN does not exist: $BIN"
    exit 1
  fi
done

echo ${BINS[@]} | fmt -w 1 >> $GITHUB_PATH
{
  echo PATHS:
  echo ${BINS[@]} | fmt -w 1
} >> setup-conda.log

log "SUCCESS" >> setup-conda.log
