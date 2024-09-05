#!/bin/zsh

# SETUP for matrix.os == ubuntu-latest

START=$SECONDS

log()
{
  printf "setup-ubuntu-latest.sh: %s\n" "$*"
}

log "Installing Ubuntu packages..."

PKGS=(
  autoconf
  default-jdk
  libcurl4-openssl-dev
  make
  tcl-dev
  zsh
)

if (
  set -eux
  sudo apt-get install -y $PKGS
) >& apt.log
then
  log "Installed Ubuntu packages in %i seconds."
else
  log "FAILED to install Ubuntu packages!"
  log "apt.log:"
  cat apt.log
  return 1
fi
