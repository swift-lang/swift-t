#!/usr/bin/env bash

# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

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
echo "$modifiers unsigned char $arrname[] = {"
data=$(mktemp -t FILE2ARRAY)
if ! (cat $infile && head -c 1 /dev/zero ) > $data; then
  rm $data
  exit 1
fi
xxd -i < $data
rm $data
echo "};"

echo "/* Size without added null byte */"
echo "$modifiers size_t ${arrname}_len = sizeof(${arrname}) - 1;"
