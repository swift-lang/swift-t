#!/bin/bash
set -eu

# CHECK TOOLS

# Checks for missing system compilers and tools

TOOLS=( ant autoconf make javac ${CC:-} swig zsh )
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
