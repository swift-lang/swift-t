#!/bin/bash
set -eu
set -o pipefail

# BUILD GENERIC SH
# Generic builder for all platforms
# RECIPE_DIR is the PLATFORM directory
# Called internally by
#        "conda build" -> PLATFORM/build.sh -> build-generic.sh
# The Swift/T build output goes into PLATFORM/build-swift-t.log
# Puts some metadata in PLATFORM/build-generic.log
#      link to work directory is important,
#      contains meta.yaml and Swift/T source
# If PLATFORM-specific settings are needed, put them in
#    PLATFORM/build.sh

# Environment notes:
# Generally, environment variables are not inherited into here.

# PREFIX is provided by Conda
# ENABLE_R may be set by meta.yaml

TIMESTAMP=$( date '+%Y-%m-%d %H:%M:%S' )
echo "BUILD-GENERIC.SH START $TIMESTAMP"

DEV_BUILD=dev/build

{
  echo "TIMESTAMP:  $TIMESTAMP"
  echo "BUILD_PWD:  $PWD"
  echo "RECIPE_DIR: $RECIPE_DIR"
  echo "SRC_DIR:    $SRC_DIR"
  echo "PREFIX:     $PREFIX"
} > $RECIPE_DIR/build-generic.log

# Cf. helpers.zsh
if [[ $PLATFORM =~ osx-* ]]
then
  NULL=""
  ZT=""
else
  NULL="--null" ZT="--zero-terminated"
fi
printenv ${NULL} | sort ${ZT} | tr '\0' '\n' > \
                                   $RECIPE_DIR/build-env.log

date >> $RECIPE_DIR/build-generic.log

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
rm -fv swift-t-settings.sh
bash init-settings.sh

SETTINGS_SED=$RECIPE_DIR/settings.sed

: ${ENABLE_R:=0}
echo ENABLE_R=$ENABLE_R
if (( ENABLE_R == 1 ))
then
  if ! which R > /dev/null
  then
    echo "build-generic.sh: Could not find R!"
    exit 1
  fi
  export R_HOME=$( R RHOME )

  cat > $RECIPE_DIR/install.R <<EOF
      # $( date )
    install.packages("RInside",
    repos="http://cran.us.r-project.org")
    if (! library("RInside",
        character.only=TRUE, logical.return=TRUE)) {
      quit(status=1)
    }
    print("Swift-RInside-SUCCESS")
EOF

  echo "build-generic.sh: Installing RInside ..."
  Rscript $RECIPE_DIR/install.R 2>&1 | tee $RECIPE_DIR/build-r.log
  if ! grep -q "Swift-RInside-SUCCESS" $RECIPE_DIR/build-r.log
  then
    echo "build-generic.sh: Installing RInside failed."
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

if [[ $PLATFORM =~ osx-* ]]
then
  SED_I=( sed -i "''" )
else
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
ln -sv ../swift-t/stc/etc/help .
ln -sv ../swift-t/turbine/etc/version.txt .

### LIB ###
cd $PREFIX/lib
ln -sv ../swift-t/stc/lib/*.jar .
# A workaround for a missing library
ln -sv libmpi.so libmpi.so.20

### SCRIPTS ###
cd $PREFIX/scripts
for file in turbine-config.sh; do
  ln -sv ../swift-t/turbine/scripts/$file .
done

echo "BUILD-GENERIC.SH STOP $( date '+%Y-%m-%d %H:%M:%S' )"
