#!/bin/bash
set -eu

# CHECK TOOLS

# Checks for missing system compilers and tools
# Do this after user swift-t-settings are loaded,
#    that may set needed modules

TOOLS=( ant autoconf make ${CC:-} swig zsh )

if [[ $SKIP != *S* ]]
then
  TOOLS+=( javac )
fi

declare -a MISSING=()

for T in ${TOOLS[@]}
do
  if ! which ${T} 2>&1 > /dev/null
  then
    MISSING+=( ${T} )
  fi
done

if (( ${#MISSING[@]} != 0 ))
then
  echo "This system is missing the following required tools:"
  for T in ${MISSING[@]}
  do
    echo ${T}
  done
  exit 1
fi

# All tools must have been found- exit with success.
exit 0
