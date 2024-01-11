#!/bin/zsh
set -eu

# Get this directory
THIS=${0:A:h}
source $THIS/../../turbine/code/scripts/helpers.zsh

if (( ${#*} != 1 )) abort "upload.sh: Provide PKG!"
PKG=$1

if [[ ! -f $PKG ]] abort "upload.sh: Not found: PKG=$PKG"

print "DIR:  ${PKG:h}"
print "FILE: ${PKG:t}"

zmodload zsh/mathfunc zsh/stat
zstat -H A -F "%Y-%m-%d %H:%M:%S" $PKG
print  "TIME: ${A[mtime]}"
printf "SIZE: %.2f\n" $(( float(${A[size]}) / (1024*1024) ))
HASH=( $( md5sum $PKG ) )
print "HASH: ${HASH[1]}"

read -t 3 _ || true

renice --priority 19 $$ >& /dev/null

START=$SECONDS
@ anaconda upload --force $PKG
STOP=$SECONDS

DURATION=$(( STOP - START ))
printf "TOOK: %5.2f s\n" $DURATION
printf "RATE: %5.2f MB/s\n" \
       $(( float(${A[size]}) / DURATION / (1024*1024) ))