#!/usr/bin/env bash
# Convert files to C arrays
# Print list of all C variable names to stdout

script_dir=$(dirname $0)

for infile in "$@"
do
  outfile=$infile.c
  arrname="file2array_$(echo "$(basename $infile)" | sed 's/[\.-]/_/g')"
  arrnames+=" $arrname"
  $script_dir/file2array.sh $infile $arrname > $outfile
  
  echo $arrname
done
