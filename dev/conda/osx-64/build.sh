
# osx-64 (Intel) BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

echo "build.sh: START"

DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

(
  set -x
  # This is needed for osx-64
  export LDFLAGS="-ltcl8.6"
  $DEV_CONDA/build-generic.sh
)

echo "build.sh: STOP"
