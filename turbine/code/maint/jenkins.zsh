#!/bin/zsh

# Jenkins script - build only

print JENKINS.ZSH

set -eu

source maint/jenkins-configure.zsh

make V=1

make V=1 install
