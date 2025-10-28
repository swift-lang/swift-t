#!/bin/zsh
set -eu

# CREATE INSTALL SH
# Make install.txt, a user-readable provenance file

OUTPUT=$1
INSTALL_PREFIX=$2
ENABLE_DEB=$3

THIS=${0:h:A}

report-timestamp()
{
  TIMESTAMP=$THIS/../../../dev/build/timestamp.txt
  if [[ -f $TIMESTAMP ]]
  then
    cat $TIMESTAMP
  else
    echo "TIMESTAMP MISSING"
  fi
}

{
  echo  "PREFIX:      $INSTALL_PREFIX"
  echo  "ENABLE_DEB:  $ENABLE_DEB"
  echo  "SOURCE:      $PWD"
  date "+DATE:        %Y-%m-%d %H:%M"

  echo -n "REPO: "
  # Use true to ignore errors (e.g., if this is not an git clone)
  if git log -n 1 > /dev/null 2>&1
  then
    git log -n 1 --pretty=format:"COMMIT: %h %aD %s%n" 2>&1
  elif (( ${CONDA_BUILD:-0} ))
  then
    echo "CONDA:"
    report-timestamp
  else
    echo "RELEASE:"
    report-timestamp
  fi
} > $OUTPUT
