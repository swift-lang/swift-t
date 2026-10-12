#!/bin/zsh -f
set -eu

# CONDA INSTALL
# Install script for use by maintainers when testing PKGs.
# Normally you will want to use a different fresh Conda
#          from the Conda you used to build the PKG.
# Provide the PKG on the command line.
# NOTE: conda install from file does not install dependencies!
#       Cf. https://docs.anaconda.com/free/anaconda/packages/install-packages
#       Thus this script installs dependencies explicitly.
# NOTE: Keep LIST in sync with meta.yaml

# Bring in utilities
# Get this directory (absolute):
DEV_CONDA=${0:A:h}
# The Swift/T Git clone:
SWIFT_T_TOP=${DEV_CONDA:h:h}
source $SWIFT_T_TOP/turbine/code/scripts/helpers.zsh
source $DEV_CONDA/helpers.zsh

help()
{
  cat <<EOF
NOTE:  This does some auto-configuration: check behavior
USAGE: Provide PKG
       Provide -C to configure (show dependencies) and exit
       Provide -D to skip installing dependencies
       Provide -P PLATFORM to change the CONDA_PLATFORM
               (else auto-detected from PKG directory)
               This is used when e.g. installing a PKG
               from a failed conda-build that is left in conda-bld/broken/
       Provide -r to install R
       Provide -s SOLVER to change the conda solver [classic,mamba]
EOF
}

# Parse the user options!
zparseopts -D -E h=H C=C D=D P:=P r=R s:=S

# Default behavior:
INSTALL_DEPS=1
USE_R=0
SOLVER=()

# Handle user flags:
if (( ${#H} )) { help ; return }
if (( ${#D} )) INSTALL_DEPS=0
if (( ${#R} )) USE_R=1
if (( ${#S} )) SOLVER=( --solver ${S[2]} )

if (( ${#*} != 1 )) abort "conda-install.sh: Provide PKG!"
PKG=$1

msg()
{
  print "conda-install.sh:" ${*}
}
msgf()
{
  printf "conda-install.sh: "
  printf "${@}"
}
fail()
{
  abort "conda-install.sh:" ${*}
}
failf()
{
  printf "conda-install.sh: "
  abortf "${@}"
}

# Report information about given PKG:
print
msg PKG=$PKG
# PKG is of form
# ANACONDA/conda-bld/PLATFORM/swift-t-V.V.V-pyVVV.tar.bz2
if (( ${#P} )) {
  CONDA_PLATFORM=${P[2]}
} else {
  # Pull out CONDA_PLATFORM directory (head then tail):
  CONDA_PLATFORM=${PKG:h:t}
}

# Catch the common mistake early: pointing PKG at a copy left in
# conda-bld/broken/ (or any other non-platform directory) auto-detects
# CONDA_PLATFORM from that directory name instead of the real platform,
# which otherwise fails much later with a confusing
# "no such file or directory: .../CONDA_PLATFORM/deps.sh" from `source`.
if [[ ! -f $DEV_CONDA/$CONDA_PLATFORM/deps.sh ]] {
  if [[ $CONDA_PLATFORM == "broken" ]] {
    fail "PKG is under conda-bld/broken/, so CONDA_PLATFORM auto-detected" \
         "as 'broken', not a real platform.  Provide the real platform" \
         "with -P, e.g. -P linux-64"
  } else {
    fail "No such CONDA_PLATFORM: '$CONDA_PLATFORM'" \
         "(expected $DEV_CONDA/$CONDA_PLATFORM/deps.sh to exist)." \
         "Provide the real platform with -P, e.g. -P linux-64"
  }
}

# Echo back platform and package statistics to the user
msg "CONDA_PLATFORM=$CONDA_PLATFORM"
zmodload zsh/stat zsh/mathfunc
zstat -H A -F "%Y-%m-%d %H:%M" $PKG
msgf "PKG: timestamp: %s size: %.1f MB\n" \
     ${A[mtime]} $(( float(${A[size]}) / (1024*1024) ))
msgf "md5sum: "
# In DEV_CONDA/helpers.zsh:
checksum $PKG
print

# Report information about active Python/Conda:
if ! which conda > /dev/null
then
  fail "no conda!"
fi

source $DEV_CONDA/get-python-version.sh

msg "using python:" $( which python ) $PYTHON_VERSION
msg "using conda: " $( which conda )

# conda env list

# Defaults:
# A SPEC is a 'package==version' specifier.
#        The version may be omitted if we are flexible.
USE_ANT=1
USE_GCC=1
USE_NCURSES=0
USE_ZSH=1
SPEC_CLANG=""
SPEC_MPICH="mpich"
# Pin Python to the PKG's ABI tag: 2026-10-09
# The PKG name carries the Python ABI, e.g. swift-t-...-py311..._3.conda.
# libtclturbine.so links libpythonX.Y.so, but 'conda install <file.conda>'
# does NOT enforce the package's own python pin, so an unpinned "python"
# lets the solver install a mismatched interpreter (e.g. 3.15) and the
# library fails to load (libpython3.11.so.1.0 not found).  Derive X.Y from
# the PKG name so the install env matches the compiled ABI.
SPEC_PYTHON="python"
PY_TAG=${${PKG:t}##*-py}      # e.g. 311h3fd9d12_3.conda
PY_NUM=${PY_TAG%%[!0-9]*}     # e.g. 311
if [[ -n $PY_NUM && $PY_NUM != $PY_TAG ]] {
  SPEC_PYTHON="python=${PY_NUM[1]}.${PY_NUM[2,-1]}"
}
export SPEC_TK=""

if [[ $CONDA_PLATFORM == "linux-64" ]] && \
   [[ $PYTHON_VERSION == 3.14*      ]]    {
  # Apparently must force this: 2026-09-30
  # Python 3.14 on linux wants to bump Tcl to 9.0
  # only at install time (not build time)
  SPEC_TK="tk=8.6.13"
}

# Load platform-specific settings:
if ! source $DEV_CONDA/$CONDA_PLATFORM/deps.sh
then
  failf "failed during: source %s\n" \
        $DEV_CONDA/$CONDA_PLATFORM/deps.sh
fi

# Build dependency list:
LIST=()
if (( USE_ANT     )) LIST+=ant
if (( USE_GCC     )) {
  # Needed for libstdc++ GLIBCXX version issue on GCE Jenkins:
  # if [[ ${JENKINS_HOME:-} != "" ]] {
  #   LIST+=( "gcc=14" )
  # } else {
  #   LIST+=(  gcc     )
  # }
  # Try plain GCC on Jenkins: 2026-10-09
  LIST+=(  gcc     )
}
if (( USE_NCURSES )) LIST+=ncurses
if (( USE_ZSH     )) LIST+=zsh
LIST+=(
  autoconf
  $SPEC_CLANG
  make
  $SPEC_MPICH
  openjdk
  # May need to pin version:
  $SPEC_PYTHON
  swig
  $SPEC_TK
)

# R switch
if (( USE_R )) {
  if [[ $CONDA_PLATFORM == "osx-arm64" ]] {
    LIST+="swift-t::emews-r"
  } else {
    # Use plain r on all other platforms:
    LIST+=r
  }
}

CONDA_FLAGS=( --yes --quiet $SOLVER )

if (( ${#C:-} )) {
  msg "flags: $CONDA_FLAGS $LIST"
  msg "configure-only: exit"
  exit
}

# For set -x (includes newline):
PS4="
+ "

# Run conda install!
if (( INSTALL_DEPS )) {
     () {
       set -x
       conda install $CONDA_FLAGS -c conda-forge $LIST
     }
}


if [[ $CONDA_PLATFORM == "osx-arm64" ]] {
  SOLVER=( --solver classic )
  CONDA_FLAGS=( --yes --quiet $SOLVER )
}

set -x
conda install $CONDA_FLAGS $PKG
