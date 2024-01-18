#!/bin/zsh
set -eu

# JENKINS BUILD SPACK SH
# Install Swift/T from CELS Jenkins with Spack under various techniques
# Developed for ANL/CELS
# Can also be run outside Jenkins for diagnosis,
#     in which case it will install under /tmp/$USER/workspace
#     and you must previously clone spack and swift-t there.
#     You have to fix the Tcl location
# We install Spack externals in advance but list them here
# Prereqs that we must build in Spack are built here
# Provide -u to uninstall first

setopt PUSHD_SILENT

DATE_FMT_NICE="%D{%Y-%m-%d} %D{%H:%M:%S}"
log()
# General-purpose log line
{
  print ${(%)DATE_FMT_NICE} ${*}
}

print
log "BUILD SPACK ..."

UNINSTALL=""
zparseopts u=UNINSTALL

WORKSPACES=/scratch/jenkins-slave/workspace
PY=$WORKSPACES/Swift-T-Python/sfw/Anaconda3
if [[ -d $PY ]] PATH=$PY/bin:$PATH

if [[ $( hostname ) == "dunedin" ]] {
  SITE="dunedin"
  # Sync this with spack-pkgs-dunedin.yaml
  TCL=/home/woz/Public/sfw/tcl-8.6.8
} else {
  SITE="gce"
  # Sync this with spack-pkgs-gce.yaml
  TCL=/scratch/$USER/workspace/Swift-T-Tcl/sfw/tcl-8.6.12
}

log "SITE:   $SITE"
log "PYTHON: $( which python )"

if [[ ! -d $TCL ]] {
  log "Tcl not found at: $TCL"
  exit 1
}

renice --priority 19 --pid $$ >& /dev/null

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
)
# Do not search for this on Dunedin
if [[ $SITE == "gce" ]] EXTERNAL_SRC_JENKINS_FINDS+=( python )

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
  gmake
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
section "START"

# Setup a default workspace when running outside Jenkins
WORKSPACE=${WORKSPACE:-/tmp/$USER/workspace}
mkdir -pv $WORKSPACE
cd        $WORKSPACE

# Setting TMP changes the spack-stage directory
# Make this publically-readable on GCE:
NAME=${WORKSPACE:t}
umask 000
whoami
# export TMP=/tmp/$USER-swift-t/$NAME/spack-stage
# log "TMP=$TMP"
SPACK_STAGE=/tmp/${USER}.swift-t
mkdir -pv $SPACK_STAGE
ls -ld $SPACK_STAGE

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

# Install packages.yaml if really under Jenkins
cp -uv $WORKSPACE/swift-t/dev/jenkins/spack-pkgs-$SITE.yaml \
       $WORKSPACE/spack/etc/spack/packages.yaml
cp -uv $WORKSPACE/swift-t/dev/jenkins/spack-cfg-gce.yaml \
       $WORKSPACE/spack/etc/spack/config.yaml

section "CHECK GIT"
for DIR in $SPACK_HOME $SWIFT_HOME
do
  pushd $DIR
  msg   $DIR
  git branch | cat
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
if [[ -f $WORKSPACE/success.txt ]] rm -v $WORKSPACE/success.txt

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

# Cannot call this SPACK_COLOR- used by spack
SP_COLOR=""
if [[ $SITE == "gce" ]] SP_COLOR=( --color never )

SPACK()
{
  log "SPACK:" ${*}
  spack $SP_COLOR ${*}
}

uninstall()
{
  # Ignore errors here - package may not yet be installed
  SPACK uninstall --all --yes --dependents $1 || true
}
uninstall-all()
{
  print
  log "UNINSTALL START"
  # Uninstall packages in reverse order
  for p in ${(Oa)PACKAGES}
  do
    uninstall $p
  done
  for p in $PREREQS
  do
    uninstall $p
  done
  log "UNINSTALL DONE."
  print
}

print
SPACK find

if (( ${#UNINSTALL} )) uninstall-all

# Start up Spack shell wrapper
source $SPACK_HOME/share/spack/setup-env.sh

log UNINSTALLXXX TCL    START
uninstall tcl
SPACK install tcl
SPACK find
log UNINSTALLXXX TCL  DONE

# Install all packages, dependencies first
(
  for p in $EXTERNAL_FINDS $EXTERNAL_SRC_JENKINS_FINDS
  do
    SPACK external find $p
    SPACK install       $p
  done

  for p in $EXTERNAL_PASTES $EXTERNAL_SRC_JENKINS_PASTES
  do
    # These are in jenkins-packages.yaml
    SPACK install $p
  done
  for p in $PREREQS
  do
    SPACK install $p
  done

  log FIND
  spack find --long --paths
  # source $SPACK_HOME/share/spack/setup-env.sh
  which spack
  log load
  spack load tcl
  print
  log which
  which tclsh tclsh8.6

  for p in $PACKAGES
  do
    SPACK install $p
  done
)

# Test the plain Swift/T installation (no Python):
SPACK load 'stc@master^turbine@master -python'
(
  set -x
  which swift-t
  swift-t -v
  swift-t -E 'trace("HELLO WORLD");'
)

SPACK install -j 1 'turbine@master+python'
SPACK install -j 1 'stc@master^turbine@master+python'

# Test the Swift/T+Python installation:
SPACK load 'stc@master^turbine@master+python'
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

print
printf "SUCCESS: duration: %i minutes.\n" $DURATION
