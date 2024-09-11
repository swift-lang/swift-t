#!/bin/zsh
set -eux

# JENKINS ANACONDA SH
# Test the Swift/T Anaconda packages
# Sets up 2 Minicondas: one in which to build   the package
#                   and one in which to install the package
# May be run interactively, just set environment variable WORKSPACE
#     and clone Swift/T in that location (which is what Jenkins does)
# Also runs as GitHub action via /.github/workflows/conda.yaml

# Environment:
# WORKSPACE:     A working directory set by Jenkins
# JENKINS_HOME:  Set by Jenkins, else unset
# GITHUB_ACTION: Set by GitHub,  else unset

setopt PUSHD_SILENT

# Defaults:
PYTHON_VERSION="39"
CONDA_TIMESTAMP="23.11.0-1"
# Examples:
# py39_23.11.0-1
# py310_23.11.0-1
# py311_23.11.0-1

DATE_FMT_NICE="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
{
  print ${(%)DATE_FMT_NICE} "anaconda.sh:" ${*}
}

# If on GitHub, pretend we are in Jenkins:
if [[ ${GITHUB_ACTION:-0} != 0 ]] WORKSPACE=$RUNNER_TEMP
if [[ ${WORKSPACE:-0} == 0 ]] {
  log "Set WORKSPACE!"
  exit 1
}

help()
{
  cat <<EOF
-p PYTHON_VERSION  default "$PYTHON_VERSION"
-c CONDA_TIMESTAMP default "$CONDA_TIMESTAMP"
-r                 install R, default does not
-u                 delete prior artifacts, default does not
EOF
}

# Run plain help as needed before possibly affecting settings:
zparseopts h=HELP
if (( ${#HELP} )) help

# Main argument processing
R=""  # May become "-r"
zparseopts -D -E -F c:=CT p:=PV r=R u=UNINSTALL
if (( ${#PV} )) PYTHON_VERSION=${PV[2]}
if (( ${#CT} )) CONDA_TIMESTAMP=${CT[2]}

if [[ ${JENKINS_HOME:-0} != 0 ]] \
  renice --priority 19 --pid $$ >& /dev/null

export TMP=$WORKSPACE/tmp-$PYTHON_VERSION

SWIFT_T_VERSION=1.6.3
log "SWIFT_T_VERSION: $SWIFT_T_VERSION"

# Self-configure
# The directory containing this script:
THIS=${0:A:h}
# The Swift/T clone:
SWIFT_T=$THIS/../..
SWIFT_T=${SWIFT_T:A}

# Set CONDA_OS, the name for our OS in the Miniconda download:
if [[ ${RUNNER_OS:-0} == "macOS" ]] {
  # On GitHub, we may be on Mac:
  CONDA_OS="MacOSX"
} else {
  CONDA_OS="Linux"
}

# Set CONDA_ARCH, the name for our chip in the Miniconda download:
# Set CONDA_PLATFORM, the name for our platform
#                     in our Anaconda builder
if [[ ${RUNNER_ARCH:-0} == "ARM64" ]] {
  # On GitHub, we may be on ARM:
  CONDA_ARCH="arm64"
  CONDA_PLATFORM="osx-arm64"
} else {
  CONDA_ARCH="x86_64"
  case $CONDA_OS {
    MacOSX) CONDA_PLATFORM="osx-64"   ;;
    Linux)  CONDA_PLATFORM="linux-64" ;;
  }
}

# The Miniconda we are working with:
CONDA_LABEL=${CONDA_TIMESTAMP}-${CONDA_OS}-${CONDA_ARCH}
MINICONDA=Miniconda3-py${PYTHON_VERSION}_${CONDA_LABEL}.sh
log "MINICONDA: $MINICONDA"
if (( ${#R} )) log "ENABLING R"

# Force Conda packages to be cached here so they are separate
#       among Minicondas and easy to delete:
export CONDA_PKGS_DIRS=$WORKSPACE/conda-cache

source $SWIFT_T/dev/conda/helpers.zsh

task()
# Run a command line verbosely and report the time in simple format:
{
  log "TASK START:" ${*}
  # Force use of GNU time program:
  if =time --format "TASK TIME: %E" ${*}
  then
    log "TASK DONE:" ${*}
    CODE=0
  else
    log "TASK FAILED:" ${*}
    CODE=1
  fi
  print
  return $CODE
}

# Clean up prior runs
uninstall()
{
  log "UNINSTALL ..."
  if [[ -d $CONDA_PKGS_DIRS ]] {
    du -sh $CONDA_PKGS_DIRS
    rm -fr $CONDA_PKGS_DIRS
  }
  rm -fv $WORKSPACE/downloads/$MINICONDA
  foreach LABEL ( build install ) \
          log "  DELETE: $WORKSPACE/sfw/Miniconda-$LABEL ..."
          rm -fr $WORKSPACE/sfw/Miniconda-$LABEL
  end
  # The Swift/T release export
  rm -fr $TMP/distro
  log "UNINSTALL OK."
}

downloads()
{
  log "DOWNLOADS ..."
  (
    # Download and install both Minicondas:
    mkdir -pv $WORKSPACE/downloads
    cd $WORKSPACE/downloads
    if [[ ! -f $MINICONDA ]] \
         wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
    foreach LABEL ( build install ) \
            if [[ ! -d $WORKSPACE/sfw/Miniconda-$LABEL ]] \
               bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-$LABEL
    end
  )
  log "DOWNLOADS OK."
}

if (( ${#UNINSTALL} )) uninstall
downloads

# Enable the build environment in Miniconda-build
PY=$WORKSPACE/sfw/Miniconda-build
PATH=$PY/bin:$PATH
source $PY/etc/profile.d/conda.sh
conda activate base
conda env list
conda update --yes conda

# THE ACTUAL TESTS:
# Create the Swift/T source release export in $TMP/distro
print
task $SWIFT_T/dev/release/make-release-pkg.zsh -T
# Set up the build environment in Miniconda-build
task $SWIFT_T/dev/conda/setup-conda.sh
# Build the Swift/T package!
task $SWIFT_T/dev/conda/conda-platform.sh $R $CONDA_PLATFORM

log "CHECKING PACKAGE..."
BLD_DIR=$WORKSPACE/sfw/Miniconda-build/conda-bld/linux-64
BZ2=$( python $SWIFT_T/dev/conda/find-pkg.py $BLD_DIR/repodata.json )
PKG=$BLD_DIR/$BZ2
if ! ls -l $PKG
then
  log "Could not find the PKG at: $PKG"
  return 1
fi
checksum $PKG
print

# Enable the install environment
log "ACTIVATING ENVIRONMENT..."
PY=$WORKSPACE/sfw/Miniconda-install
PATH=$PY/bin:$PATH
source $PY/etc/profile.d/conda.sh
conda activate base
conda env list
log "ACTIVATED ENVIRONMENT."
print

task $SWIFT_T/dev/conda/conda-install.sh $PKG

log "TRY SWIFT/T..."
(
  set -x
  PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
  which swift-t
  swift-t -v
  swift-t -E 'trace(42);'
)
print
log "SWIFT/T OK."
log "PKG=$PKG"
print
