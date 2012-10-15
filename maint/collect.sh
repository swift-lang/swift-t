#!/bin/sh

# Take the single-word tokens from the given *.d files
# one per line in an output file

OUTPUT=$1
shift
INPUT=${*}

# Puts one word per line; strip spaces, backslash, colon
# Strip out object files *.o, *.po, and dependency files *.d
# Make unique

cat ${INPUT} | \
  fmt -w 1 | cut -f 1  | \
  sed 's/ //g;s/\\//g;s/://g' | \
  grep -v '.*\.o\>\|.*\.po\>\|.*\.d\>' | \
  sort -u > ${OUTPUT}
