
# osx-64 (Intel) BUILD SH
# Simply calls build-generic.
# `conda build` calls this as Bash.

echo "build.sh: START"

DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

(
  set -eu

  # Find Java bin directory in Conda PREFIX sandbox
  # echo CONDA_EXE=$CONDA_EXE
  # CONDA=$( dirname $( dirname $CONDA_EXE ) )
  # OpenJDK home should be under PREFIX/pkgs/openjdk-*
  # Should be in PREFIX/bin but is not on any system
  # On Linux it is under           $PREFIX/pkgs/openjdk-*/lib/jvm/bin
  # On GitHub macos-13 it is under $PREFIX/lib/jvm/bin
  echo FIND JAVA PREFIX $PREFIX
  which java javac || true
  conda list
  # Does not exist:
  # source $PREFIX/etc/profile.d/conda.sh
  set -x
  find $PREFIX
  which java javac || true
  echo $PATH
  set -x
  FOUND_JDK=0
  find $PREFIX -name java
  JDKS=( $( find $PREFIX/pkgs -type d -name "openjdk-*" ) )
  if (( ${#JDKS} > 0 ))
  then
    JDK_BIN=${JDKS[0]}/lib/jvm/bin
    if ! [[ -d $JDK_BIN ]]
    then
      echo "build.sh: Broken JVM directory structure in $PREFIX"
      exit 1
    fi
    FOUND_JDK=1
  fi
  if [[ -d $PREFIX/lib/jvm/bin ]]
  then
    JDK_BIN=$PREFIX/lib/jvm/bin
    FOUND_JDK=1
  fi
  if (( ! FOUND_JDK ))
  then
    echo "build.sh: Could not find OpenJDK in $CONDA"
    exit 1
  fi
  echo "build.sh: Found OpenJDK bin directory: $JDK_BIN"
  PATH=$JDK_BIN:$PATH
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
) 2>&1 | dd

echo "build.sh: STOP"
