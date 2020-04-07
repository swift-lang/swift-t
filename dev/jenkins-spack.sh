#!/bin/zsh
set -eu

# JENKINS SPACK SH
# Install Swift/T from Jenkins under various techniques

mkdir -pv /tmp/ExM/jenkins-spack
cd /tmp/ExM/jenkins-spack

if [[ ! -d spack ]]
then
  git clone https://github.com/spack/spack.git
fi
