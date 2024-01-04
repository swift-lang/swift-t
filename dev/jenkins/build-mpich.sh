#!/bin/zsh
set -eu

# JENKINS BUILD MPICH SH
# Install Swift/T from Git under with MPICH on CELS Jenkins
# Can also be run interactively on GCE,
#     if on the correct compute server!
#     just set environment variable WORKSPACE
#     -> Uses hard-coded dependencies from other Jenkins projects

setopt PUSHD_SILENT
setopt PIPE_FAIL

# Get the Swift/T source directory, canonicalized:
SWIFT_T_SRC=${ZSH_ARGZERO:A:h:h:h}
# Formulate the installation directory:
SWIFT_T_SFW=${WORKSPACE/sfw}

# Directory containing all Jenkins workspaces
WORKSPACE_ROOT=/scratch/jenkins-slave/workspace

cd $SWIFT_T_SRC

# Passed to build-swift-t.sh:
B=""
zparseopts -B=B

renice --priority 19 --pid $$ >& /dev/null

source dev/helpers.sh

# Look at timestamps left by previous runs and see if git has changed
GIT_CHANGED=1
print "New timestamp:"
git-log | tee timestamp-new.txt
if [[ -r timestamp-old.txt ]] {
  print "Old timestamp:"
  cat timestamp-old.txt
  if diff -q timestamp-{old,new}.txt
  then
    GIT_CHANGED=0
  fi
}
print GIT_CHANGED=$GIT_CHANGED
if (( ! GIT_CHANGED )) {
  print "Git did not change - exit."
  exit
}
print

# Define and reset the settings file:
SETTINGS=dev/build/swift-t-settings.sh
rm -fv $SETTINGS
dev/build/init-settings.sh

# Pre-installed tools:
PATH=/home/woz/Public/sfw/ant-1.9.4/bin:$PATH
PATH=/home/woz/Public/sfw/x86_64/jdk-1.8.0_91/bin:$PATH

# Products from other Jenkins projects:
MPICH=$WORKSPACE_ROOT/Swift-T-MPICH/sfw/mpich-4.0.3
TCL=$WORKSPACE_ROOT/Swift-T-Tcl/sfw/tcl-8.6.12
PYTHON=$WORKSPACE_ROOT/Swift-T-Python/sfw/Miniconda
PYTHON_EXE=$PYTHON/bin/python
PATH=$PYTHON/bin:$PATH
PATH=$MPICH/bin:$PATH

# Create a sed file and apply to swift-t-settings:
cat > settings.sed <<EOF
/SWIFT_T_PREFIX=/s@=.*@=$SWIFT_T_SFW@
s/\\# TCL_INSTALL/TCL_INSTALL/
/TCL_INSTALL=/s@=.*@=$TCL@
/ENABLE_PYTHON=/s@=.*@=1@
/PYTHON_EXE=/s@=.*@=$PYTHON_EXE@
EOF

# Can add this for faster interactive builds:
# /PARALLELISM=/s@=.*@=3@

sed -i -f settings.sed $SETTINGS

print
print "Running build-swift-t.sh ..."
# Run the build!
dev/build/build-swift-t.sh ${B} |& tee build.out
# Produce build.out for shell inspection later.

# See if it worked:
PATH=$SWIFT_T_SFW/stc/bin:$PATH
set -x
swift-t -v
swift-t -E 'trace(42);'

# Prevent future rebuild until Git changes
#         or someone deletes timestamp-old.txt
cp -v timestamp-{new,old}.txt
