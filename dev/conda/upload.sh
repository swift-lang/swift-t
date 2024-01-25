#!/bin/zsh
set -eu

# UPLOAD SH
# Upload the PKG to Anaconda

# Get this directory
THIS=${0:A:h}
source $THIS/../../turbine/code/scripts/helpers.zsh

FORCE=""
zparseopts -D -E f=F
if (( ${#F} )) FORCE="--force"

if (( ${#*} != 1 )) abort "upload.sh: Provide PKG!"
PKG=$1

if [[ ! -f $PKG ]] abort "upload.sh: Not found: PKG=$PKG"

print "DIR:  ${PKG:h}"
print "FILE: ${PKG:t}"

zmodload zsh/mathfunc zsh/stat
zstat -H A -F "%Y-%m-%d %H:%M:%S" $PKG
print  "TIME: ${A[mtime]}"
printf "SIZE: %.1f MB\n" $(( float(${A[size]}) / (1024*1024) ))
HASH=( $( md5sum $PKG ) )
print "HASH: ${HASH[1]}"

# Mac renice does not respect --version
PRIORITY="--priority"
if ! renice --version >& /dev/null
then
  PRIORITY=""
fi
renice $PRIORITY 19 $$ >& /dev/null

printf "CONFIRM? "
read -t 30 _ && print "YES" || print "TIMEOUT"
print

START=$SECONDS
@ anaconda upload $FORCE $PKG
STOP=$SECONDS

DURATION=$(( STOP - START ))
printf "TOOK: %5.2f s\n" $DURATION
printf "RATE: %5.2f MB/s\n" \
       $(( float(${A[size]}) / DURATION / (1024*1024) ))

# Local Variables:
# buffer-file-coding-system:utf-8-unix
# End:
