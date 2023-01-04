#!/bin/zsh
set -eu

# JENKINS SPACK SH
# Install Swift/T from GCE Jenkins with Spack under various techniques
# We install Spack externals in advance but list them here
# Prereqs that we must build in Spack are built here

WORKSPACES=/scratch/jenkins-slave/workspace
PY=$WORKSPACES/Swift-T-Python/sfw/Anaconda3
PATH=$PY/bin:$PATH

START=${SECONDS}

# The packages we want to test in Spack:
PACKAGES=(
  spack install exmcutils@master
  spack install adlbx@master
  spack install turbine@master
  spack install stc@master
)

# Prereqs needed to install in Spack (not external):
PREREQS=(
  libiconv
  libsigsegv
  pcre
  pkgconf
  readline
  zlib
)

# Prereqs that we build from source as externals as Jenkins projects
# Add to PATH, then do 'spack external find ; spack install'
EXTERNAL_SRC_JENKINS_FINDS=(
  mpich
  python
)
# Packages Spack cannot 'external find'
# Add to packages.yaml, then do 'spack install'
EXTERNAL_SRC_JENKINS_PASTES=(
  tcl
)

# Prereqs already available on GCE with spack external find
# Just do 'spack external find ; spack install'
EXTERNAL_FINDS=(
  autoconf
  automake
  berkeley-db
  bzip2
  diffutils
  jdk
  libtool
  m4
  ncurses
  swig
)

# Packages Spack cannot 'external find'
# Paste into packages.yaml then do 'spack install'
EXTERNAL_PASTES=(
  ant
  zsh
)

setopt PUSHD_SILENT

git-log()
{
  git log -n 1 --date=format:"%Y-%m-%d %H:%M" \
               --pretty=format:"%h %ad %s%n"
}

git-hash ()
{
  git log -n 1 --pretty=format:"%h"
}

git-log


# Set a default workspace when running outside Jenkins
WORKSPACE=${WORKSPACE:-/tmp/${USER}/workspace}

SPACK_HOME=$WORKSPACE/spack

renice --priority 19 --pid $$
mkdir -pv $WORKSPACE
pushd     $WORKSPACE

pwd
ls

SPACK_CHANGED=0
# True if the last build was a success
PRIOR_SUCCESS=0

if [[ -f $WORKSPACE/success.txt ]] {
  print "Found: $WORKSPACE/success.txt"
  PRIOR_SUCCESS=1
}

if [[ ! -d spack ]] {
  (
    set -x
    git clone https://github.com/spack/spack.git
    cd spack
    git checkout develop
    cp -v $WORKSPACE/dev/jenkins-packages.yaml etc/spack/packages.yaml
  )
  SPACK_CHANGED=1
}

pushd $SPACK_HOME
git branch
print "Old hash:"
git-hash | tee hash-old.txt
git-log
if ! git pull
then
  print "WARNING: git pull failed!"
fi
print "New hash:"
git-hash | tee hash-new.txt
git-log
if ! diff -q hash-{old,new}.txt
then
  SPACK_CHANGED=1
fi
popd

print SPACK_CHANGED=$SPACK_CHANGED

if (( ! SPACK_CHANGED && PRIOR_SUCCESS )) {
  print
  print "No Spack changes - exit."
  print
  return
}

# New run: reset success.txt
rm -v $WORKSPACE/success.txt || true

set -x
ls $SPACK_HOME/bin
PATH=$SPACK_HOME/bin:$PATH
which spack
set +x

uninstall()
{
  # Ignore errors here - package may not yet be installed
  spack uninstall --all --yes --dependents $1 || true
}
uninstall-all()
{
  # Uninstall packages in reverse order
  for p in ${(Oa)PACKAGES}
  do
    uninstall ${p}
  done
  for p in ${PREREQS}
  do
    uninstall ${p}
  done
}

(
  set -x
  spack find
)

# uninstall-all

(
  set -x
  for p in ${EXTERNAL_FINDS} ${EXTERNAL_SRC_JENKINS_FINDS}
  do
    spack external find ${p}
    spack install       ${p}
  done
  for p in ${EXTERNAL_PASTES} ${EXTERNAL_SRC_JENKINS_PASTES}
  do
    # These are in jenkins-packages.yaml
    spack install ${p}
  done
  for p in ${PREREQS}
  do
    spack install ${p}
  done
  for p in ${PACKAGES}
  do
    spack install ${p}
  done
)

source ${SPACK_HOME}/share/spack/setup-env.sh

# Test the plain Swift/T installation (no Python):
spack load 'stc@master^turbine@master -python'
(
  set -x
  which swift-t
  swift-t -v
  swift-t -E 'trace("HELLO WORLD");'
)

spack install 'turbine@master+python'
spack install 'stc@master^turbine@master+python'

# Test the Swift/T+Python installation:
spack load 'stc@master^turbine@master+python'
(
  set -x
  which swift-t
  swift-t -i python -E 'trace(python("", "repr(42)"));'
)

# nice spack install 'stc@master^turbine@master+python+r'

touch $WORKSPACE/success.txt

STOP=${SECONDS}
DURATION=$(( (STOP-START) / 60 ))  # whole minutes

printf "SUCCESS: duration: %i minutes.\n" ${DURATION}
