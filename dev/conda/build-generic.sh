#!/bin/bash
set -eu
set -o pipefail

# BUILD GENERIC SH
# Generic builder for all platforms

# Environment notes:
# Generally, environment variables are not inherited into here.
# PREFIX is provided by Conda
# ENABLE_R may be set by meta.yaml

echo "BUILD.SH START $( date '+%Y-%m-%d %H:%M:%S' )"
echo PREFIX $PREFIX

install -d $PREFIX/bin
install -d $PREFIX/etc
install -d $PREFIX/lib
install -d $PREFIX/scripts
install -d $PREFIX/swift-t

build_dir=dev/build

cd $build_dir
rm -fv swift-t-settings.sh
bash init-settings.sh

SETTINGS_SED=$RECIPE_DIR/settings.sed
echo ENABLE_R=${ENABLE_R:-0}

if (( ${ENABLE_R:-0} == 1 ))
then
  if ! which R > /dev/null
  then
    echo "build.sh: Could not find Rscript!"
    exit 1
  fi
  export R_HOME=$( R RHOME )
  R --vanilla --no-echo \
    -e 'install.packages("RInside", repos="http://cran.us.r-project.org")'
fi

# Edit swift-t-settings
sed -i -f $SETTINGS_SED swift-t-settings.sh

# Build it!
# Merge output streams to try to prevent buffering
#       problems with conda build
./build-swift-t.sh 2>&1 | tee build-swift-t.out

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

echo "BUILD.SH STOP $( date '+%Y-%m-%d %H:%M:%S' )"
