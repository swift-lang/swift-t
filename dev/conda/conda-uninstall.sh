#!/bin/zsh
set -eu

# CONDA UNINSTALL
# Uninstall Swift/T and any cached packages from the Anaconda in PATH
# Useful before reinstalling in a test installation

# Report information about active Python/Conda:
if ! which conda >& /dev/null
then
  print "No conda!"
  return 1
fi

# Get this directory
THIS=${0:A:h}
source $THIS/../../turbine/code/scripts/helpers.zsh
LOG_LABEL="conda-uninstall.sh:"

print
PY=$( which python )
PY=${PY:h:h}
log "using python in:"
log $PY
C=$( which conda )
C=${C:h:h}
log "using conda in:"
log $C
print

printf "CONFIRM? "
read -t 10 _ || true
print

CONDA_PREFIX=${CONDA_EXE:h:h}

if conda uninstall --yes swift-t
then
  : OK
else
  log "proceeding..."
fi


SWIFTS=( $CONDA_PREFIX/pkgs/swift-t-*(/) )
log "found ${#SWIFTS} Swift/T directories"

for S in $SWIFTS
do
  rm -rf $S
done

SWIFTS=( $CONDA_PREFIX/pkgs/swift-t-* )
for S in $SWIFTS
do
  rm -v $S
done

log "OK"

# Local Variables:
# buffer-file-coding-system:utf-8-unix
# End:
