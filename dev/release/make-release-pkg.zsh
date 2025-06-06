#!/usr/bin/env zsh
set -eu

# MAKE-RELEASE-PKG.ZSH

# Builds swift-t-<version>.tar.gz for distribution

# End-user should be able to compile Swift/T
# with standard tools: make, gcc, ant, javac, etc.
# End-user should not need: autotools, patch

# usage:
# ./make-release-pkg.zsh [-h]
# Provide -h for help

# Environment:
# TMP: Fast working directory. Default /tmp.  Works in TMP/distro

# How this works:
# Section 0:   Define some helper functions
# Section I:   Setup
# Section II:  Run autotools
# Section III: Copy (export) into TMP/distro/RELEASE
# Section IV:  Make tar.gz from export directory

# TOOLS: On Linux, we use 'cp -u --parents'
#        On Mac, we do this manually (slower)
#        Specify Mac mode with the flag -m
#        Alternatively, you can install GNU coreutils via Homebrew

# NOTE:  We use zsh/files here so many common tools are
#        replaced with ZSH builtins for speed
#        (unless called via 'command')
zmodload zsh/files

# Get this directory (absolute):
THIS=${0:A:h}
# Top level of Swift/T Git clone
TOP=${THIS:h:h}

# Work from the top level:
cd ${TOP}

# Define RELEASE numbers
source $TOP/dev/get-versions.sh

SWIFT_K_VERSION=swift-k-NONE

# If USE_MASTER=1, use master instead of release numbers
export USE_MASTER=0

# If ENABLE_COASTER, enable coaster support
export ENABLE_COASTER=0

# Signal to lower-level scripts that this is a source package
export PKG_TYPE=src

# Run ./bootstrap by default; may be disabled
BOOTSTRAP=1

# Make tar.gz by default; may be disabled
MAKE_TAR=1

# Use Linux tools by default; may be set to "Mac"
OS="Linux"

# Verbose: blank, or '-v' for verbose operations
V=""

# Make pushd silent:
setopt PUSHD_SILENT
# Allow for additional glob patterns:
setopt KSH_GLOB

help()
{
  print "See file header for usage."
  print " -b : skip bootstrap (autotools) step for speed"
  print " -h : show help"
  print " -m : use Mac tools"
  print " -t : call this 'master' instead of a release number"
  print " -T : do not make a tar.gz"
  print " -v : make verbose"
}

while getopts "bchmtTv" opt
do
  case ${opt} in
    b) BOOTSTRAP=0      ;;
    c) ENABLE_COASTER=1 ;;
    h) help ; exit      ;;
    m) OS="Mac"         ;;
    t) USE_MASTER=1     ;;
    T) MAKE_TAR=0       ;;
    v) V="-v"           ;;
    \?)
      print "make-release-package.zsh: unknown option: ${OPTARG}"
      help
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
    SWIFT_T_RELEASE=swift-t-${SWIFT_T_VERSION}-coaster
  else
    SWIFT_T_RELEASE=swift-t-${SWIFT_T_VERSION}
  fi
  STC_RELEASE=release/${STC_VERSION}
  TURBINE_RELEASE=release/${TURBINE_VERSION}
  ADLB_RELEASE=release/${ADLBX_VERSION}
  C_UTILS_RELEASE=release/${CUTILS_VERSION}
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

if [[ ${OS} == "Linux" ]]
then

# Copy into export directory
export_copy()
{
  cp -u ${V}  --parents ${*} ${TARGET}
}

export_copy_dir()
{
  cp -ur ${V} --parents ${*} ${TARGET}
}

else  # Mac does not have 'cp --parents'

# Copy into export directory
export_copy()
{
  local F D
  for F in ${*}
  do
    # ZSH dirname:
    D=${F:h}
    mkdir -p ${TARGET}/${D}
    cp ${V} ${F} ${TARGET}/${D}
  done
}

export_copy_dir()
{
  local F D
  for F in ${*}
  do
    # ZSH dirname:
    D=${F:h}
    mkdir -p           ${TARGET}/${D}
    cp    -r ${V} ${F} ${TARGET}/${D}
  done
}

fi

distclean()
{
  if [[ -f Makefile ]]
  then
    make distclean
  fi
}

# SECTION I

TMP=${TMP:-/tmp}
DISTRO=${TMP}/distro

SWIFT_K=https://github.com/swift-lang/swift-k.git

# Output the key settings:
declare DISTRO
declare SWIFT_T_RELEASE
EXPORT=${DISTRO}/${SWIFT_T_RELEASE}/${SWIFT_T_RELEASE}
declare EXPORT

# SECTION II

if (( BOOTSTRAP ))
then
  for D in ${TOP}/{c-utils,lb,turbine}/code
  do
    print
    pushd ${D}
    print "BOOTSTRAP: ${D}"
    ./bootstrap
    popd
  done
fi

pushd stc/code
print
print "AUTOCONF: STC"
autoconf
popd

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
command mkdir -p ${V} ${EXPORT}
pushd ${TOP}

# c-utils
print "Copying c-utils..."
TARGET=${EXPORT}/c-utils/code
mkdir -p ${TARGET}
pushd ${TOP}/c-utils/code
FILE_LIST=( $( maint/file-list.zsh ) )
export_copy ${FILE_LIST}
popd
if (( ${#V} )) printf "OK\n\n"

# LB
print "Copying ADLB/X..."
TARGET=${EXPORT}/lb/code
mkdir -p ${TARGET}
pushd ${TOP}/lb/code
FILE_LIST=( $( maint/file-list.zsh ) )
export_copy ${FILE_LIST}
popd
if (( ${#V} )) printf "OK\n\n"

# Turbine
print "Copying Turbine..."
TARGET=${EXPORT}/turbine/code
mkdir -p ${TARGET}
pushd ${TOP}/turbine/code
FILE_LIST=( $( maint/file-list.zsh ) )
export_copy ${FILE_LIST}
popd
if (( ${#V} )) printf "OK\n\n"

# STC
print "Copying STC..."
pushd ${TOP}/stc
pushd code
TARGET=${EXPORT}/stc/code
mkdir -p ${TARGET}
FILE_LIST=( $( maint/file-list.zsh ) )
export_copy ${FILE_LIST}
popd
pushd tests
TARGET=${EXPORT}/stc/tests
mkdir -p ${TARGET}
export_copy *.sh run-*.zsh
export_copy {make-package.tcl,valgrind-patterns.grep}
export_copy **/*.swift
popd
popd
if (( ${#V} )) printf "OK\n\n"

# Build scripts
print "Copying build scripts..."
TARGET=${EXPORT}/dev/build
mkdir -p ${TARGET}
pushd ${TOP}/dev/build
export_copy *.template !(swift-t-settings).sh
popd
TARGET=${EXPORT}/dev/m4
mkdir -p ${TARGET}
pushd ${TOP}/dev/m4
export_copy *.m4
popd
if (( ${#V} )) printf "OK\n\n"

# Make timestamp
P="%~"
print "Timestamp in ${(%)P} :"
{
  FIELDWIDTH="%-15s"
  printf ${FIELDWIDTH} "GIT TIMESTAMP:"
  git log -n 1 '--date=format:%Y-%m-%d %H:%M' \
               '--pretty=format:%ad%n'
  printf ${FIELDWIDTH} "GIT HASH:"
  git log -n 1 '--pretty=format:%H%n'
  printf ${FIELDWIDTH} "GIT MESSAGE:"
  git log -n 1 '--pretty=format:%s%n'
  printf ${FIELDWIDTH} "PKG TIMESTAMP:"
  date "+%Y-%m-%d %H:%M"
} | tee ${EXPORT}/dev/build/timestamp.txt
print

if (( ENABLE_COASTER ))
then
  TARGET=${EXPORT}/coaster-c-client
  mkdir -p ${TARGET}
  pushd ${TOP}/coaster-c-client
  export_copy aclocal.m4 configure.ac configure config/** m4/**
  export_copy **/Makefile.in **/Makefile.am
  export_copy AUTHORS ChangeLog COPYING INSTALL NEWS README
  export_copy src/**/*.{cpp,h}
  export_copy tcl/**
  export_copy_dir tests
  popd

  TARGET=${EXPORT}/swift-k
  rm -rf ${TARGET}
  cp -ur ${V} swift-k/dist/swift-svn ${TARGET}
  echo "This is a compiled Swift/K distribution." > ${TARGET}/README
  echo "Source is available at ${SWIFT_K}\
        (branch ${SWIFT_K_RELEASE}." >> ${TARGET}/README
fi

# Pop back up to TOP
popd

if (( MAKE_TAR ))
then
  # Create tarball from contents of export/
  print "Creating tar.gz ..."
  pushd ${DISTRO}/${SWIFT_T_RELEASE}
  RELEASE_TGZ=${SWIFT_T_RELEASE}.tar.gz
  tar cfz ${RELEASE_TGZ} ${SWIFT_T_RELEASE}

  print "Swift/T package created at $(pwd)/${RELEASE_TGZ}"
  du -h  ${RELEASE_TGZ}
  md5sum ${RELEASE_TGZ}
else
  print "Swift/T exported to ${DISTRO}/${SWIFT_T_RELEASE}"
fi
