#!/bin/zsh
set -eu

# JENKINS ANACONDA SH
# Test the Swift/T Anaconda packages
# Sets up 2 Minicondas: one in which to build   the package
#                   and one in which to install the package
# May be run interactively, just set environment variable WORKSPACE

# Defaults:
PYTHON_VERSION="39"
CONDA_LABEL="23.11.0-1"
# py39_23.11.0-1
# py310_23.11.0-1
# py311_23.11.0-1
UNINSTALL="" PV="" CL=""
zparseopts u=UNINSTALL c:=CL p:=PV
if (( ${#PV} )) PYTHON_VERSION=${PV[2]}
if (( ${#CL} )) CONDA_LABEL=${CL[2]}

renice --priority 19 --pid $$

setopt PUSHD_SILENT

DATE_FMT_NICE="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
{
  print ${(%)DATE_FMT_NICE} ${*}
}

SWIFT_T_VERSION=1.6.3
log "SWIFT_T_VERSION: $SWIFT_T_VERSION"

# The Miniconda we are working with:
MINICONDA=Miniconda3-py${PYTHON_VERSION}_${CONDA_LABEL}-Linux-x86_64.sh
log "MINICONDA: $MINICONDA"

# Force Conda packages to be cached here so they are separate
#       among Minicondas and easy to delete:
export CONDA_PKGS_DIRS=$WORKSPACE/conda-cache

task()
# Run a command line verbosely and report the time in simple format:
{
  log "TASK START:" ${*}
  if /bin/time --format "TASK TIME: %E" ${*}
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
  rm -fr $WORKSPACE/downloads/swift-t
  rm -fr /tmp/distro
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

cd $WORKSPACE/downloads

if [[ -d swift-t ]]
then
  cd swift-t
  git checkout master
  task git pull
  cd -
else
  task git clone https://github.com/swift-lang/swift-t.git
fi

# THE ACTUAL TESTS:
# Create the "exported" Swift/T source tree in /tmp/distro
print
task swift-t/dev/release/make-release-pkg.zsh
# Set up the build environment in Miniconda-build
task swift-t/dev/conda/setup-conda.sh
# Build the Swift/T package!
task swift-t/dev/conda/linux-64/conda-platform.sh

log "CHECKING PACKAGE..."
BZ2=swift-t-${SWIFT_T_VERSION}-py${PYTHON_VERSION}_1.tar.bz2
PKG=$WORKSPACE/sfw/Miniconda-build/conda-bld/linux-64/$BZ2
if ! ls -l $PKG
then
  log "Could not find the PKG at: $PKG"
  return 1
fi
md5sum $PKG
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

task swift-t/dev/conda/conda-install.sh $PKG

log "TRY SWIFT/T..."
(
  set -x
  PATH=$WORKSPACE/sfw/Miniconda-install/bin:$PATH
  which swift-t
  swift-t -v
  swift-t -E 'trace(42);'
)
log "SWIFT/T OK."
print
