#!/bin/zsh

# MAKE-STC-DOCS
# 1) Extracts snippets for code samples
# 2) Runs asciidoc

set -eu

cd $( dirname $0 )

# Extract snippet from file.
snip()
{
  N=$1
  FILE=$2
  # Insert -snip-NUMBER into file name
  OUTPUT=${FILE/\./-snip-${N}.}
  examples/snippet.pl -n=${N} ${FILE} > ${OUTPUT}
}

# Check if file $1 is uptodate wrt $2
# $1 is uptodate if it exists and is newer than $2
# If $2 does not exist, crash
uptodate()
{
  if [[ ${#} < 2 ]]
  then
    print "uptodate: Need at least 2 args!"
    return 1
  fi

  local OPTION
  local VERBOSE=0
  while getopts "v" OPTION
  do
    case ${OPTION}
      in
      v)
        VERBOSE=1 ;;
    esac
  done
  shift $(( OPTIND-1 ))

  local TARGET=$1
  shift
  local PREREQS
  PREREQS=( ${*} )

  local f
  for f in ${PREREQS}
  do
    if [[ ! -f ${f} ]]
    then
      ((VERBOSE)) && print "not found: ${f}"
      return 1
    fi
  done

  if [[ ! -f ${TARGET} ]]
  then
    ((VERBOSE)) && print "does not exist: ${TARGET}"
    return 1
  fi

  local CODE
  for f in ${PREREQS}
  do
    [[ ${TARGET} -nt ${f} ]]
    CODE=${?}
    if (( ${CODE} == 0 ))
    then
      ((VERBOSE)) && print "${TARGET} : ${f} is uptodate"
    else
      ((VERBOSE)) && print "${TARGET} : ${f} is not uptodate"
      return ${CODE}
    fi
  done
  return ${CODE}
}

{ sed 's/^/> /' examples/3/test-b-build.sh
  cat          examples/3/test-b-run.sh   } > examples/3/test-b.sh
snip 1 examples/6/func.f90
snip 1 examples/6/prog-f90.f90
snip 1 examples/6/prog-swift.swift
snip 1 examples/8/f.c

DOCS=( guide leaf internals )

for NAME in ${DOCS}
do
  if ! uptodate ${NAME}.html ${NAME}.txt
  then
    print "asciidoc ${NAME}.txt"
    asciidoc --attribute stylesheet=${PWD}/swift.css -a max-width=750px -a textwidth=80 ${NAME}.txt
  fi
done
