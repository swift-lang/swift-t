#!/usr/bin/env bash
# Convert file to a C array with provided name
# Also produce length variable of type size_t with _len suffix

if [ $# != 2 ]
then
  echo "Usage: $0 <input file> <c array variable name>"
  exit 1
fi
infile=$1
arrname=$2

echo "const char $arrname[] = {"
xxd -i < $infile
echo "};"

echo "const size_t ${arrname}_len = sizeof(${arrname});"
