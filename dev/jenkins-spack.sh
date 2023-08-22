#!/bin/zsh
set -eu

# JENKINS SPACK SH
# Developed for ANL/GCE
# Install Swift/T from GCE Jenkins with Spack under various techniques
# We install Spack externals in advance but list them here
# Prereqs that we must build in Spack are built here
# Provide -u to uninstall first

UNINSTALL=""
zparseopts u=UNINSTALL

WORKSPACES=/scratch/jenkins-slave/workspace
PY=$WORKSPACES/Swift-T-Python/sfw/Anaconda3
PATH=$PY/bin:$PATH

setopt PUSHD_SILENT

START=$SECONDS

# The packages we want to test in Spack:
PACKAGES=(
  exmcutils@master
  adlbx@master
  turbine@master
  stc@master
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

@()
# Verbose command
{
  print ">>" ${*}
  ${*}
}

git-log()
{
  git log -n 1 --date=format:"%Y-%m-%d %H:%M" \
               --pretty=format:"%h %ad %s%n"
}

DATE_FMT_S="%D{%Y-%m-%d} %D{%H:%M:%S}"

msg()
{
  print ${(%)DATE_FMT_S} "JENKINS-SPACK:" ${*}
}

section()
{
  print ""
  msg ${*}
}

# Allow Git checkout to complete?
sleep 10
section START

# Setup a default workspace when running outside Jenkins
WORKSPACE=${WORKSPACE:-/tmp/$USER/workspace}
mkdir -pv $WORKSPACE
cd        $WORKSPACE

SPACK_HOME=$WORKSPACE/spack
SWIFT_HOME=$WORKSPACE/swift-t

# True if either git changed
GIT_CHANGED=0
# True if the last build was a success
PRIOR_SUCCESS=0

if [[ -f $WORKSPACE/success.txt ]] {
  print "Found: $WORKSPACE/success.txt"
  PRIOR_SUCCESS=1
}

# Install packages.yaml
cp -uv $WORKSPACE/swift-t/dev/jenkins-packages.yaml \
       $WORKSPACE/spack/etc/spack/packages.yaml

section "CHECK GIT"
for DIR in $SPACK_HOME $SWIFT_HOME
do
  pushd $DIR
  msg   $DIR
  @ git branch
  touch hash-old.txt
  print "Old hash:"
  cat hash-old.txt
  print "New hash:"
  git-log | tee hash-new.txt
  if ! diff -q hash-{old,new}.txt
  then
    print "Git changed in $DIR"
    GIT_CHANGED=1
    cp -v hash-{new,old}.txt
    popd
    break
  fi
  popd
done

print GIT_CHANGED=$GIT_CHANGED

if (( ! GIT_CHANGED && PRIOR_SUCCESS )) {
  print
  print "No Spack changes - exit."
  print
  return
}

# New run: reset success.txt
rm -v $WORKSPACE/success.txt || true

PATH=$SPACK_HOME/bin:$PATH
(
  set -x
  which spack
)

# echo spack find
# spack find
# echo spack load
# spack load tcl
# set -x
# which tclsh8.6 tclsh || true
# tclsh8.6 < /dev/null
# set +x

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
    uninstall $p
  done
  for p in $PREREQS
  do
    uninstall $p
  done
}

print
@ spack find

if (( ${#UNINSTALL} )) uninstall-all

# Install all packages, dependencies first
(
  set -x
  for p in $EXTERNAL_FINDS $EXTERNAL_SRC_JENKINS_FINDS
  do
    spack external find $p
    spack install       $p
  done
  for p in $EXTERNAL_PASTES $EXTERNAL_SRC_JENKINS_PASTES
  do
    # These are in jenkins-packages.yaml
    spack install $p
  done
  for p in $PREREQS
  do
    spack install $p
  done
  for p in $PACKAGES
  do
    spack install $p
  done
)

source $SPACK_HOME/share/spack/setup-env.sh

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

# Prevent future rebuilds until Git changes
touch $WORKSPACE/success.txt

STOP=$SECONDS
DURATION=$(( (STOP-START) / 60 ))  # whole minutes

printf "SUCCESS: duration: %i minutes.\n" $DURATION
