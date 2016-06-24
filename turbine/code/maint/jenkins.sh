#!/bin/zsh
set -eu

# Jenkins script - build only

print JENKINS.ZSH

source maint/jenkins-configure.sh

make V=1

make V=1 install
