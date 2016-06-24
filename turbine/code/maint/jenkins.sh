#!/bin/zsh
set -eu

# Jenkins script - build only

print JENKINS.ZSH

source maint/jenkins-configure.zsh

make V=1

make V=1 install
