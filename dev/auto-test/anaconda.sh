#!/bin/zsh
set -eu

# AUTO-TEST ANACONDA SH
# Test the Swift/T Anaconda packages under Jenkins or GitHub Actions

# May be run interactively, just:
#     set environment variable WORKSPACE
#     and clone Swift/T in that location (which is what Jenkins does)
#     set environment variable CONDA_OS={Linux, MacOSX}
#     set environment variable CONDA_PLATFORM={linux-64, osx-arm64}

# Also runs as GitHub action via /.github/workflows/conda.yaml
#      in which case we create artifact anaconda.log

# Sets up 2 Minicondas: one in which to build   the package
#                   and one in which to install the package

# This script reuses downloads and sfw/Miniconda installations:
#      provide -u to uninstall first

# Environment:
# WORKSPACE:      A working directory set by Jenkins
# JENKINS_HOME:   Set by Jenkins,           else unset
# GITHUB_ACTIONS: Set by GitHub to "true",  else unset or "false"

# Defaults:
PYTHON_VERSION="310"
# Examples:
# py310_23.11.0-1
# py311_23.11.0-1

# For set -x (includes newline):
PS4="
+ "

DATE_FMT_NICE="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
{
  local LOG_PFX  #  Log prefix
  if [[ ${GITHUB_ACTIONS:-false} == false ]] {
    LOG_PFX=${(%)DATE_FMT_NICE}
  } else {
    # GitHub already includes timestamps:
    LOG_PFX=""
  }
  print $LOG_PFX "anaconda.sh:" ${*}
}

abort()
{
  log "ABORT" ${*}
  exit 1
}

# If on GitHub, pretend we are in Jenkins by setting the
# WORKSPACE directory to the GitHub equivalent RUNNER_TEMP
if [[ ${GITHUB_ACTIONS:-false} == true ]] {
  log "Start..." >> anaconda.log
  WORKSPACE=$RUNNER_TEMP
}

if [[ ${WORKSPACE:-0} == 0 ]] abort "Set WORKSPACE!"

help()
{
  cat <<EOF
-a                 Artifact- bundle package into ./PKG.conda.tar
                   used to make an artifact in GitHub Actions
                   (artifact names are fixed; the tar contents have
                    the original *.conda file name, which must
                    be retained)
-b                 disable bootstrapping during make-release-pkg
                   runs faster if after a successful bootstrap
-c CONDA_TIMESTAMP default "$CONDA_TIMESTAMP"
-p PYTHON_VERSION  default "$PYTHON_VERSION"
-r R_VERSION       install R, default does not
-u                 delete prior artifacts, default does not
-uu                delete conda cache, default does not
-v                 verbose
EOF
  exit
}

# Run plain help as needed before possibly affecting settings
#                                 shown by help as defaults:
zparseopts h=HELP
if (( ${#HELP} )) help

# Main argument processing
A=""     # May become "-a"
B=""     # May become "-b"
R=""     # May become ( -r R_VERSION )
USE_R="" # May become "-r"
zparseopts -D -E -F a=A b=B c:=CT p:=PV r:=R u+=UNINSTALL v=VERBOSE
if (( ${#PV} )) PYTHON_VERSION=${PV[2]}
if (( ${#CT} )) CONDA_TIMESTAMP=${CT[2]}
if (( ${#R}  )) USE_R="-r"

# For make-release-pkg
export TMP=$WORKSPACE/tmp-$PYTHON_VERSION

# SWIFT_T_VERSION=1.6.3
# log "SWIFT_T_VERSION: $SWIFT_T_VERSION"
# Remove any dot from PYTHON_VERSION, e.g., 3.11 -> 311
PYTHON_VERSION=${PYTHON_VERSION/\./}
log "PYTHON_VERSION:  $PYTHON_VERSION"

# Conda timestamps for each Python version are updated regularly:
typeset -A CONDA_TIMESTAMPS
CONDA_TIMESTAMPS=(
  310 25.9.1-3
  311 23.11.0-2
  312 24.11.1-0
  313 25.5.1-0
)
if [[ ! -v CONDA_TIMESTAMPS[$PYTHON_VERSION] ]] \
  abort "Unknown PYTHON_VERSION=$PYTHON_VERSION"
CONDA_TIMESTAMP=${CONDA_TIMESTAMPS[$PYTHON_VERSION]}
log "CONDA_TIMESTAMP: $CONDA_TIMESTAMP"

# Force Conda packages to be cached here so they are separate
#       among Minicondas and easy to delete:
export CONDA_PKGS_DIRS=$WORKSPACE/conda-cache
log "CONDA_PKGS_DIRS: $CONDA_PKGS_DIRS"

# Self-configure
# The directory containing this script:
THIS=${0:A:h}
# The Swift/T clone:
SWIFT_T=$THIS/../..
SWIFT_T=${SWIFT_T:A}

if [[ ${CONDA_OS:-0} == 0 ]] {
  # Set CONDA_OS, the name for our OS in the Miniconda download:
  if [[ ${RUNNER_OS:-0} == "macOS" ]] {
    # On GitHub, we may be on Mac:
    CONDA_OS="MacOSX"
  } else {
    CONDA_OS="Linux"
  }
}

if [[ ${CONDA_PLATFORM:-0} == 0 ]] {
  # Set CONDA_ARCH, the name for our chip in the Miniconda download:
  # Set CONDA_PLATFORM, the name for our platform
  #                     in our Anaconda builder
  if [[ ${RUNNER_ARCH:-0} == "ARM64" ]] {
    # On GitHub, we may be on ARM:
    CONDA_PLATFORM="osx-arm64"
  } else {
    CONDA_ARCH="x86_64"
    case $CONDA_OS {
      "MacOSX") CONDA_PLATFORM="osx-64"   ;;
      "Linux")  CONDA_PLATFORM="linux-64" ;;
      *)        abort "Unknown CONDA_OS=$CONDA_OS" ;;
    }
  }
}

case $CONDA_PLATFORM {
  "linux-64")  CONDA_ARCH="x86_64" ;;
  "osx-arm64") CONDA_ARCH="arm64"  ;;
  *)           abort "Unknown CONDA_PLATFORM=$CONDA_PLATFORM" ;;
}

log CONDA_PLATFORM=$CONDA_PLATFORM

# The Miniconda we are working with:
CONDA_LABEL=${CONDA_TIMESTAMP}-${CONDA_OS}-${CONDA_ARCH}
MINICONDA=Miniconda3-py${PYTHON_VERSION}_${CONDA_LABEL}.sh
log "MINICONDA: $MINICONDA"
if (( ${#R} )) log "ENABLING R"

source $SWIFT_T/dev/conda/helpers.zsh

# If running in CELS Jenkins, reduce priority, clean Anaconda cache:
if [[ ${JENKINS_HOME:-0} != 0 ]] {
  renice --priority 19 --pid ${$} >& /dev/null
  log "CLEANING ANACONDA CACHE:"
  python $SWIFT_T/dev/jenkins/conda_delete_1.py \
         --rate 0.2 $CONDA_PKGS_DIRS
}

# Detect GNU time program
GNU_TIME=0
if =time -v true >& /dev/null
then
  GNU_TIME=1
fi

# Two possible time commands:
if (( GNU_TIME )) {
  tm()
  {
    =time --format "TASK TIME: %E" ${*}
  }
} else {
  tm()
  {
    =time -h ${*}
  }
}

task()
# Run a command line verbosely and report the time in simple format:
{
  log "TASK START:" ${*}
  if tm ${*}
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
    log "  CONDA CACHE:"
    du -sh $CONDA_PKGS_DIRS
  } else {
    log "  CONDA CACHE: does not exist"
    mkdir -pv $CONDA_PKGS_DIRS
  }
  if (( ${#UNINSTALL} > 1 )) && [[ -d $CONDA_PKGS_DIRS ]] {
    log "  DELETE: $CONDA_PKGS_DIRS ..."
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
  log "DOWNLOADING ..."
  (
    # Download and install both Minicondas:
    mkdir -pv $WORKSPACE/downloads
    cd $WORKSPACE/downloads
    if [[ ! -f $MINICONDA ]] {
      log "download: $MINICONDA"
      wget --no-verbose https://repo.anaconda.com/miniconda/$MINICONDA
    }
    foreach LABEL ( build install ) \
            if [[ ! -d $WORKSPACE/sfw/Miniconda-$LABEL ]] {
               log "install:  $WORKSPACE/sfw/Miniconda-$LABEL"
               bash $MINICONDA -b -p $WORKSPACE/sfw/Miniconda-$LABEL
            }
    end
  )
  log "DOWNLOADS OK."
}

# Enable the install environment
do-activate()
{
  PY=$1
  log "ACTIVATING ENVIRONMENT: $PY ..."
  PATH=$PY/bin:$PATH
  # Allow unset variables for:
  # activate_clang:16: CMAKE_PREFIX_PATH: parameter not set
  # Python 3.11 2025-04-25
  set +eu
  source $PY/etc/profile.d/conda.sh
  conda activate base
  # conda env list
  log "ACTIVATED ENVIRONMENT:  $PY"
  # Suppress a warning about default channel:
  conda config --add channels defaults
  log "CONDA UPDATING: $PY ..."
  conda update --quiet --yes --solver classic conda
  log "CONDA UPDATE: OK: $PY"
  set -eu
  print
}

check-pkg()
{
  BLD_DIR=$WORKSPACE/sfw/Miniconda-build/conda-bld/$CONDA_PLATFORM
  REPODATA=$BLD_DIR/repodata.json
  log "CHECKING PACKAGE in $BLD_DIR ..."

  if ! PKG_FILE=$( python $SWIFT_T/dev/conda/find-pkg.py $REPODATA )
  then
    print
    log "CHECKING PACKAGE FAILED!"
    print
    return 1
  fi
  PKG_PATH=$BLD_DIR/$PKG_FILE
  log "CHECKING PKG at $PKG_PATH ..."
  if ! ls -l $PKG_PATH
  then
    log "Could not find the PKG at: $PKG_PATH"
    return 1
  fi
  checksum $PKG_PATH
  print
}

try-swift-t()
{
  log "TRY SWIFT/T..."
  PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
  () {
    set -x
    which swift-t
    swift-t -v
    swift-t -E 'trace(42);'
  }
  print
  log "SWIFT/T OK."
  print
}

log-success()
{
  if [[ ${GITHUB_ACTIONS:-false} == true ]] {
    # Record success if on GitHub:
    {
      log "SUCCESS"
      log "PKG=$PKG_PATH"
    } | tee -a anaconda.log
  }
}

# The main test logic follows:

if (( ${#UNINSTALL} )) uninstall
downloads

do-activate $WORKSPACE/sfw/Miniconda-build

# Build the PKG!
# Create the Swift/T source release export in $TMP/distro
task $SWIFT_T/dev/release/make-release-pkg.zsh $B -T
# Set up the build environment in Miniconda-build
task $SWIFT_T/dev/conda/setup-conda.sh
# Build the Swift/T package!
task $SWIFT_T/dev/conda/conda-build.sh $R $CONDA_PLATFORM $VERBOSE

# Check that the PKG was built
check-pkg

# Activate the install environment
do-activate $WORKSPACE/sfw/Miniconda-install

# Install the new package into the install environment!
task $SWIFT_T/dev/conda/conda-install.sh $USE_R $PKG_PATH

try-swift-t

if (( ${#A} )) {
  log "CREATING ARTIFACT ..."
  task tar -cv -f PKG.conda.tar -C $BLD_DIR $PKG_FILE
}

log-success
