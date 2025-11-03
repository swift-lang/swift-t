# osx-arm64 BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

exec 2>&1

# Try to force everything to stdout for correct ordering
# Works on Mac: 2025-10-27
echo "build.sh: START"

DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

(
  export LDFLAGS="-ltcl8.6"

  set -x
  $DEV_CONDA/build-generic.sh
)

echo "build.sh: STOP"
