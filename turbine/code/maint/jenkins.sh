#!/bin/zsh
set -eu

# Turbine Jenkins script - build only

print JENKINS.ZSH

source maint/jenkins-configure.sh

# Ignore autoscan warning for AC_PROG_MAKE_SET

make V=1

make V=1 install
