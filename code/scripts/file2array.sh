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
if [ $# != 2 ]
then
  echo "Usage: $0 <input file> <c array variable name>"
  exit 1
fi
infile=$1
arrname=$2

echo "/*FILE2ARRAY:$arrname:$infile*/"
echo "#include <stddef.h>" # For size_t
echo
echo "const char $arrname[] = {"
xxd -i < $infile
echo ", 0x0"
echo "};"

echo "/* Size without added null byte */"
echo "const size_t ${arrname}_len = sizeof(${arrname}) - 1;"
