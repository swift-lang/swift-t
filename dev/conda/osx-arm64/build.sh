
# osx-arm64 BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

echo "build.sh: START"

DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )
export USE_OSX_ARM64=1

(
  set -x
  $DEV_CONDA/build-generic.sh
)

echo "build.sh: STOP"
