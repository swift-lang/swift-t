#!/bin/bash
set -eu
set -o pipefail

# BUILD GENERIC SH
# Generic builder for all platforms
# RECIPE_DIR is the CONDA_PLATFORM directory
# Called internally by
#        "conda build" -> CONDA_PLATFORM/build.sh -> build-generic.sh
# The Swift/T build output goes into CONDA_PLATFORM/build-swift-t.log
# Puts some metadata in CONDA_PLATFORM/build-generic.log
#      link to work directory is important,
#      contains meta.yaml and Swift/T source
# If CONDA_PLATFORM-specific settings are needed, put them in
#    CONDA_PLATFORM/deps.sh

# Environment notes:
# Generally, environment variables are not inherited into here.

# PREFIX is provided by Conda
# ENABLE_R may be set by meta.yaml

TIMESTAMP=$( date '+%Y-%m-%d %H:%M:%S' )
echo "BUILD-GENERIC.SH START $TIMESTAMP"

# This is in the exported Swift/T source tree
DEV_BUILD=dev/build
# This is in the builder RECIPE_DIR source tree
DEV_CONDA=$( cd $RECIPE_DIR/.. ; /bin/pwd -P )

: ${ENABLE_R:=0}
echo ENABLE_R=$ENABLE_R

{
  echo "TIMESTAMP:  $TIMESTAMP"
  echo "BUILD_PWD:  $PWD"
  echo "RECIPE_DIR: $RECIPE_DIR"
  echo "SRC_DIR:    $SRC_DIR"
  echo "PREFIX:     $PREFIX"
  echo "ENABLE_R:   $ENABLE_R"
} > $RECIPE_DIR/build-generic.log

if [[ $CONDA_PLATFORM =~ osx-* ]]
then
  NULL=""
  ZT=""
  if [[ $CONDA_PLATFORM =~ osx-arm64 ]]
  then
    # These variables affect the mpicc/mpicxx wrappers
    export MPICH_CC=clang
    export MPICH_CXX=clang++
  fi
  export ENABLE_CONDA_LINUX=0
else
  NULL="--null"
  ZT="--zero-terminated"
  export ENABLE_CONDA_LINUX=1
fi
printenv ${NULL} | sort ${ZT} | tr '\0' '\n' > \
                                   $RECIPE_DIR/build-env.log

if [[ ! -d $DEV_BUILD ]]
then
  # This directory disappears under certain error conditions
  # The user must clean up the work directory
  echo "Cannot find DEV_BUILD=$DEV_BUILD under $PWD"
  echo "Delete this directory and the corresponding work_moved"
  echo $PWD
  echo "See build-generic.log for SRC_DIR"
  exit 1
fi

install -d $PREFIX/bin
install -d $PREFIX/etc
install -d $PREFIX/lib
install -d $PREFIX/scripts
install -d $PREFIX/swift-t

# Start build!
cd $DEV_BUILD
if [[ ! -f init-settings.sh ]]
then
  # OS may have cleaned up the /tmp directories
  echo "build-generic.sh: Cannot find init-settings.sh!"
  echo "build-generic.sh: PWD=$PWD"
  exit 1
fi
rm -fv swift-t-settings.sh
bash init-settings.sh

SETTINGS_SED=$RECIPE_DIR/settings.sed

if (( ENABLE_R == 1 ))
then
  # For the R-enabled installer, we build/install RInside into our
  #     Anaconda R installation, and install [de]activate scripts.
  echo
  echo "build-generic.sh: Checking R ..."
  if ! which R
  then
    echo "build-generic.sh: Could not find R!"
    exit 1
  fi
  export R_HOME=$( R RHOME )

  echo "build-generic.sh: Installing RInside into $R_HOME ..."
  Rscript $DEV_CONDA/install-RInside.R 2>&1 | \
    tee $RECIPE_DIR/install-RInside.log
  if ! grep -q "Swift-RInside-SUCCESS" $RECIPE_DIR/install-RInside.log
  then
    echo "build-generic.sh: Installing RInside failed."
    exit 1
  fi
  echo "build-generic.sh: Installing RInside done."

  # Copy the [de]activate scripts to $PREFIX/etc/conda/[de]activate.d.
  # This will allow them to be run on environment activation.
  for CHANGE in "activate" "deactivate"
  do
    mkdir -p "${PREFIX}/etc/conda/${CHANGE}.d"
    cp "${RECIPE_DIR}/../${CHANGE}.sh" "${PREFIX}/etc/conda/${CHANGE}.d/${PKG_NAME}_${CHANGE}.sh"
  done
fi

if [[ $CONDA_PLATFORM =~ osx-* ]] && [[ ${GITHUB_ACTION:-0} == 0 ]]
then
  # Use this syntax on Mac, unless in GitHub,
  #     where we install Homebrew gnu-sed
  echo "using Mac sed."
  SED_I=( sed -i "''" )
else
  echo "using Linux sed."
  SED_I=( sed -i )
fi

# Edit swift-t-settings
${SED_I[@]} -f $SETTINGS_SED swift-t-settings.sh

# Build it!
# Merge output streams to try to prevent buffering
#       problems with conda build
{
  echo "BUILD SWIFT-T START: $( date '+%Y-%m-%d %H:%M:%S' )"
  ./build-swift-t.sh -vv 2>&1
  echo "BUILD SWIFT-T STOP:  $( date '+%Y-%m-%d %H:%M:%S' )"
} | tee $RECIPE_DIR/build-swift-t.log

# Setup symlinks for utilities

### BIN ###
cd $PREFIX/bin
for file in stc swift-t helpers.zsh; do
  ln -sv ../swift-t/stc/bin/$file .
done
for file in turbine; do
  ln -sv ../swift-t/turbine/bin/$file .
done

### ETC ###
cd $PREFIX/etc
for file in stc-config.sh turbine-version.txt; do
  ln -sv ../swift-t/stc/etc/$file .
done
ln -sv ../swift-t/stc/etc/version.txt stc-version.txt
ln -sv ../swift-t/stc/etc/help .
ln -sv ../swift-t/turbine/etc/version.txt .

### LIB ###
cd $PREFIX/lib
ln -sv ../swift-t/stc/lib/*.jar .

### SCRIPTS ###
cd $PREFIX/scripts
for file in turbine-config.sh; do
  ln -sv ../swift-t/turbine/scripts/$file .
done

set -x
ls $PREFIX/bin

echo "BUILD-GENERIC.SH STOP $( date '+%Y-%m-%d %H:%M:%S' )"
