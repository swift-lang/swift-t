#!/bin/zsh -f
set -eu

# MAKE-RELEASE-PKG.ZSH

# Builds exm-<token>.tar.gz for distribution

# End-user should be able to compile Swift/T
# with standard tools: make, gcc, ant, javac, etc.
# End-user should not need: autotools, patch

# usage:
# ./construct.zsh

# Environment:
# TMP: Fast working directory. Default /tmp.  Works in TMP/distro

# How this works:
# Section 0:   Define some helper functions
# Section I:   Setup
# Section II:  Run autotools
# Section III: Copy (export) into TMP/distro/RELEASE
# Section IV:  Make tar.gz

# Define RELEASE numbers
EXM_VERSION=0.9.0
STC_VERSION=0.7.0
TURBINE_VERSION=0.8.0
ADLB_VERSION=0.7.0
C_UTILS_VERSION=0.5.0
SWIFT_K_VERSION=swift-t-0.9.0

# If USE_MASTER=1, use master instead of release numbers
export USE_MASTER=0

# If ENABLE_COASTER, enable coaster support
export ENABLE_COASTER=0

# If PACKAGE_DEBIAN=1, build Debian binary packages instead
PACKAGE_DEBIAN=0

DISTRO_HOME=$( cd $( dirname $0 ) ; /bin/pwd )

while getopts "cpt" opt
do
  case ${opt} in
    c)
      ENABLE_COASTER=1
      ;;
    p)
      PACKAGE_DEBIAN=1
      ;;
    t)
      USE_MASTER=1
      ;;
    \?)
      echo "construct.zsh: unknown option: ${OPTARG}"
      exit 1
      ;;
  esac
done

# Define tokens (location in SVN or Git):
# If using release paths, use "release/version" or git branch/tag name
# If using master (USE_MASTER), use "master"
if (( ! USE_MASTER ))
then
  if (( ENABLE_COASTER ))
  then
    SWIFT_T_RELEASE=swift-t-${EXM_VERSION}-coaster
  else
    SWIFT_T_RELEASE=swift-t-${EXM_VERSION}
  fi
  STC_RELEASE=release/${STC_VERSION}
  TURBINE_RELEASE=release/${TURBINE_VERSION}
  ADLB_RELEASE=release/${ADLB_VERSION}
  C_UTILS_RELEASE=release/${C_UTILS_VERSION}
  SWIFT_K_RELEASE=${SWIFT_K_VERSION}
else
  SWIFT_T_RELEASE=swift-t-master
  STC_RELEASE=master
  TURBINE_RELEASE=master
  ADLB_RELEASE=master
  C_UTILS_RELEASE=master
  SWIFT_K_RELEASE=master
fi

declare SWIFT_T_RELEASE STC_RELEASE TURBINE_RELEASE \
        ADLB_RELEASE C_UTILS_RELEASE SWIFT_K_RELEASE

# SECTION 0

crash()
{
  print ${*}
  exit 1
}

# Copy into export directory
export_copy()
{
  cp -uv --parents ${*} ${TARGET}
}

export_copy_dir()
{
  cp -uvr --parents ${*} ${TARGET}
}

distclean()
{
  if [[ -f Makefile ]]
  then
    make distclean
  fi
}

# SECTION I

# Directory containing this script
THIS=$( cd $( dirname $0 ) ; /bin/pwd )
# Top level of Swift/T Git clone
TOP=$( cd ${THIS}/../../ ; /bin/pwd )

TMP=${TMP:-/tmp}
DISTRO=${TMP}/distro

SWIFT_K=https://github.com/swift-lang/swift-k.git

# Output the key settings:
declare DISTRO
declare SWIFT_T_RELEASE
EXPORT=${DISTRO}/${SWIFT_T_RELEASE}/${SWIFT_T_RELEASE}
declare EXPORT

# SECTION II

for D in ${TOP}/{c-utils,lb,turbine}/code
do
  print
  pushd ${D}
  print "BOOTSTRAP: ${D}"
  # ./bootstrap
  popd
done

if (( ENABLE_COASTER ))
then
  swiftk_log="$(pwd)/swift-k.log"
  print "COMPILE: swift-k"
  echo "Swift/K compile output in $swiftk_log"
  pushd swift-k
  git checkout remotes/origin/${SWIFT_K_RELEASE}
  ant redist > "$swiftk_log" 2>&1 || \
    crash "Swift/K compile FAILED!"
  popd

  print "SETUP: coaster-c-client"
  pushd coaster-c-client
  git checkout remotes/origin/${SWIFT_K_RELEASE}
  ./autogen.sh || \
    crash "Coaster C client autogen.sh FAILED!"
  popd
fi

# SECTION III

print
mkdir -pv ${EXPORT}
pushd ${TOP}

# c-utils
print "Copying c-utils..."
TARGET=${EXPORT}/c-utils/code
mkdir -pv ${TARGET}
pushd c-utils/code
export_copy c-utils-config.h.in configure configure.ac
export_copy bootstrap Makefile.in src/module.mk.in
export_copy maint/*.sh
export_copy src/*.[ch]
export_copy c-utils-config.h.in src/c-utils.h.in
export_copy tests/module.mk.in
export_copy tests/*.[ch]
export_copy version.txt NOTICE debian/control
popd
printf "OK\n\n"

# LB
print "Copying ADLB/X..."
TARGET=${EXPORT}/lb/code
mkdir -pv ${TARGET}
pushd lb/code
export_copy bootstrap configure configure.ac Makefile.in install-sh
export_copy config.h.in
export_copy maint/*.sh
export_copy maint/*.c
export_copy src/*.[ch]
export_copy config.h.in
export_copy src/{adlb-version.h.in,mpe-settings.h.in}
export_copy {src,tests}/module.mk.in
export_copy version.txt NOTICE debian/control
popd
printf "OK\n\n"

# Turbine
print "Copying Turbine..."
TARGET=${EXPORT}/turbine/code
mkdir -pv ${TARGET}
pushd turbine/code
export_copy bootstrap config.h.in configure configure.ac
export_copy version.txt
export_copy Makefile.in **/*.mk.in
export_copy maint/{*.sh,find-tcl.zsh}
export_copy bin/turbine
export_copy bin/turbine-{read,write}-doubles
export_copy scripts/*-config.sh.in scripts/helpers.zsh
export_copy **/*.[chi] **/*.{tcl,swift}
export_copy src/executables/turbine_sh.manifest
export_copy tests/{runbin.zsh.in,run-mpi.zsh}
export_copy tests/{*.manifest,*.sh,*.data,*.txt}
export_copy src/util/debug-tokens.tcl.in
export_copy src/**/*.manifest
export_copy tests/runbin.zsh.in
export_copy scripts/{data-log.sh,leak-find.py,rank.zsh}
export_copy scripts/main-wrap/genleaf
export_copy scripts/main-wrap/{settings/*.sh,templates/*template*}
export_copy scripts/mkstatic/*(.)
export_copy scripts/submit/*.*sh*
export_copy scripts/submit/cray/*.*sh*
export_copy scripts/submit/cobalt/*turbine*.*sh*
export_copy scripts/submit/ec2/turbine-setup-ec2.zsh
export_copy scripts/submit/pbs/{turbine-pbs-run.zsh,turbine.pbs.m4}
export_copy scripts/submit/slurm/*turbine*.*sh*
export_copy src/turbine/turbine-version.h.in
export_copy etc/help*.txt
popd
printf "OK\n\n"

# STC
print "Copying STC..."
pushd stc
pushd code
TARGET=${EXPORT}/stc/code
mkdir -pv ${TARGET}
export_copy build.xml
export_copy etc/version.txt
export_copy etc/help*.txt etc/turbine-version.txt
export_copy src/exm/stc/ast/ExM.g
export_copy bin/* scripts/stc-config.sh
export_copy conf/stc-env.sh.template
export_copy **/*.java
export_copy lib/*.jar
export_copy META-INF/MANIFEST.MF
popd
pushd tests
TARGET=${EXPORT}/stc/tests
mkdir -pv ${TARGET}
export_copy *.sh run-*.zsh
export_copy {make-package.tcl,valgrind-patterns.grep}
export_copy **/*.swift
popd
popd
printf "OK\n\n"

# Build scripts
print "Copying build scripts..."
TARGET=${EXPORT}/dev/build
mkdir -pv ${TARGET}
pushd dev/build
export_copy *.sh *.txt
# Create swift-t-settings.sh from template
cp -uv swift-t-settings.sh.template ${EXPORT}/swift-t-settings.sh
export_copy swift-t-settings.sh{,.template} locate-src-root.sh
popd
printf "OK\n\n"

if (( ENABLE_COASTER ))
then
  TARGET=${EXPORT}/coaster-c-client
  mkdir -p ${TARGET}
  pushd coaster-c-client
  export_copy aclocal.m4 configure.ac configure config/** m4/**
  export_copy **/Makefile.in **/Makefile.am
  export_copy AUTHORS ChangeLog COPYING INSTALL NEWS README
  export_copy src/**/*.{cpp,h}
  export_copy tcl/**
  export_copy_dir tests
  popd

  TARGET=${EXPORT}/swift-k
  rm -rf ${TARGET}
  cp -uvr swift-k/dist/swift-svn ${TARGET}
  echo "This is a compiled Swift/K distribution." > ${TARGET}/README
  echo "Source is available at ${SWIFT_K}\
        (branch ${SWIFT_K_RELEASE}." >> ${TARGET}/README
fi

# Pop back up to TOP
popd

# Create tarball from contents of export/
print "Creating tar.gz ..."
pushd ${DISTRO}/${SWIFT_T_RELEASE}
RELEASE_TGZ=${SWIFT_T_RELEASE}.tar.gz
tar cfz ${RELEASE_TGZ} ${SWIFT_T_RELEASE}

print "Swift/T package created at $(pwd)/${RELEASE_TGZ}"

du -h ${RELEASE_TGZ}
