#!/usr/bin/env bash
# Convert file to a C array with provided name.
#
# Also produce length variable of type size_t with _len suffix.
#
# We add an extra null byte on the end of the array, which is not
# included in the length, to allow it to be used as a string if needed.
#
# We will add a C comment to the file of the form for indexing purposes:
#
# /*FILE2ARRAY:<c array variable name>:<input file>*/
#
set -e

arrname=""
modifiers=""

usage () {
  echo "Usage: $0 [ -v <c array variable name> ] [ -m <array variable modifiers> ]\
 <input file> " >&2
  exit 1
}

while getopts "v:m:" opt; do
  case $opt in 
    v) 
      if [[ $arrname != "" ]]; then
        echo "-v specified twice" >&2
        usage
      fi
      arrname=$OPTARG
      ;;
    m)
      if [[ $modifiers != "" ]]; then
        echo "-m specified twice" >&2
        usage
      fi
      modifiers=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      usage
  esac
done
shift $((OPTIND - 1))

infile=$1
if [[ $# > 1 ]]; then
  echo "Too many remaining arguments: $@" >&2
  usage
fi

if [ -z "$modifiers" ]; then
  # Default is const with global linking visibility
  modifiers="const"
fi

echo "/*FILE2ARRAY:$arrname:$infile*/"
echo "#include <stddef.h>" # For size_t
echo
echo "const char $arrname[] = {"
(cat $infile && head -c 1 /dev/zero ) | xxd -i 
echo "};"

echo "/* Size without added null byte */"
echo "$modifiers size_t ${arrname}_len = sizeof(${arrname}) - 1;"
