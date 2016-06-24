#!/bin/zsh
set -eu

# Jenkins script - build only

print JENKINS.ZSH

source maint/jenkins-configure.sh

cat export/blob.swift

make clean

make V=1

make V=1 install
