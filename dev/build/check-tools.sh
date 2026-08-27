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

# All tools must have been found
echo

java_version_error()
{
  local TOOL=$1
  echo "ERROR: version 21 or later is required for $TOOL"
  echo "       found version $JAVA_VERSION"
  echo "       $TOOL:" $( which $TOOL )
  exit 1
}

if [[ $SKIP != *S* ]]
then
  # Check Java versions
  JAVA_VERSION_REQUIRED=21
  source $THIS/java_version.sh
  JAVA_VERSION=$(  get_java_major_version java  )
  JAVAC_VERSION=$( get_java_major_version javac )
  if [[ "$JAVA_VERSION" == "" ]]
  then
    echo "WARNING: Could not determine java version"
  else
    echo "java  version: $JAVA_VERSION"
    if (( JAVA_VERSION < JAVA_VERSION_REQUIRED ))
    then
      java_version_error java
    fi
  fi
  if [[ "$JAVAC_VERSION" == "" ]]
  then
    echo "WARNING: Could not determine javac version"
  else
    echo "javac version: $JAVAC_VERSION"
    if (( JAVAC_VERSION < JAVA_VERSION_REQUIRED ))
    then
      java_version_error javac
    fi
  fi
fi

# All checks passed - return with success
