
# osx-64 (Intel) BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

echo "build.sh: START"

DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

(
  set -eu

  # Find Java bin directory
  echo CONDA_EXE=$CONDA_EXE
  CONDA=$( dirname $( dirname $CONDA_EXE ) )
  # OpenJDK home should be under MINICONDA/pkgs/openjdk-*
  OPENJDK=( $( find $CONDA/pkgs -type d -name "openjdk-*" ) )
  if (( ${#OPENJDK} == 0 ))
  then
    echo "build.sh: Could not find OpenJDK in $CONDA"
    exit 1
  fi
  if ! [[ -d ${OPENJDK[0]}/lib/jvm/bin ]]
  then
    echo "build.sh: Could not find OpenJDK binaries in $CONDA"
    exit 1
  fi
  echo "build.sh: Found OpenJDK: $OPENJDK"
  PATH=${OPENJDK[0]}/lib/jvm/bin:$PATH
  which java javac

  if (( ${ENABLE_R:-0} ))
  then
    SDK=$( xcrun --show-sdk-path )
    LDFLAGS="-L$SDK/usr/lib -lSystem "
    LDFLAGS+="-F$SDK/System/Library/Frameworks"
    # -L/tmp/celsloaner/Miniconda-310-R/pkgs/tk-8.6.12-h5d9f67b_0/lib
  fi

  # This is needed for osx-64
  LDFLAGS+=" -ltcl8.6"
  export LDFLAGS

  echo "build.sh: calling build-generic.sh ..."
  $DEV_CONDA/build-generic.sh
)

echo "build.sh: STOP"
