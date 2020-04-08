#!/bin/zsh
set -eu

# JENKINS SPACK SH
# Install Swift/T from Jenkins under various techniques

setopt PUSHD_SILENT

git-log()
{
  git log -n 1 --color=always --date="format:%Y-%m-%d %H:%M" --pretty=format:"%Cblue%h%Creset %ad %Cgreen%s%Creset%n"
}

soft add +git-2.10.1

set -x

which git
git --version

git-log

ls -l

mkdir -pv /tmp/ExM/jenkins-spack
pushd /tmp/ExM/jenkins-spack

if [[ ! -d spack ]]
then
  git clone https://github.com/spack/spack.git
  pushd spack
  git checkout develop
  popd
fi

pushd spack
git-log
git pull
git-log
popd

PATH=/tmp/ExM/jenkins-spack/spack/bin:$PATH

which spack

spack install stc@master
