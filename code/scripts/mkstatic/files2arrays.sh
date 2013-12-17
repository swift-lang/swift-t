#!/usr/bin/env bash
# Convert files to C arrays
# First argument is sed regular expression to rename input files to output files
# Print list of all C variable names to stdout

script_dir=$(dirname $0)

regex=$1
shift;

for infile in "$@"
do
  outfile=$( echo "$infile" | sed "$regex" )
  arrname="file2array_$(basename "$infile" | sed 's/[\.-]/_/g')"
  arrnames+=" $arrname"
  $script_dir/file2array.sh -a $arrname $infile > $outfile
  
  echo $arrname
done
