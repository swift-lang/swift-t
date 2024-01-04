#!/bin/sh
# set -eu

# CREATE INSTALL SH
# Make install.txt, a user-readable provenance file

OUTPUT=$1
INSTALL_PREFIX=$2
ENABLE_DEB=$3

{
  echo  "PREFIX:      $INSTALL_PREFIX"
  echo  "ENABLE_DEB:  $ENABLE_DEB"
  echo  "SOURCE:      $PWD"
  date "+DATE:        %Y-%m-%d %H:%M"

  echo -n "REPO: "
  # Use true to ignore errors (e.g., if this is not an git clone)
  git log -n 1 --pretty=format:"COMMIT: %h %aD %s%n" 2>&1 || true
} > $OUTPUT
