#!/bin/zsh
set -eu

# JENKINS SPACK SH
# Install Swift/T from Jenkins under various techniques

setopt PUSHD_SILENT
soft add +git-2.10.1

git-log()
{
  git log -n 1 --date="format:%Y-%m-%d %H:%M" --pretty=format:"%Cblue%h%Creset %ad %Cgreen%s%Creset%n"
}

git-log

mkdir -pv /tmp/ExM/jenkins-spack
pushd /tmp/ExM/jenkins-spack

set -x

if [[ ! -d spack ]]
then
  git clone https://github.com/spack/spack.git
  pushd spack
  git checkout develop
  popd
fi

SPACK_HOME=/tmp/ExM/jenkins-spack/spack

SPACK_CHANGED=0
pushd $SPACK_HOME
git-log | tee timestamp-old.txt
git pull
git-log | tee timestamp-new.txt
popd
if ! diff -q timestamp-{old,new}.txt
then
  SPACK_CHANGED=1
fi

PATH=$SPACK_HOME/bin:$PATH

which spack

cp -uv ~wozniak/Public/data/packages-mcs.yaml \
   $SPACK_HOME/etc/spack/packages.yaml

set -x
nice spack install exmcutils@master
nice spack install adlbx@master
nice spack install turbine@master
nice spack install stc@master
set +x

source ${SPACK_HOME}/share/spack/setup-env.sh

spack load stc
which swift-t

swift-t -v
swift-t -E 'trace("HELLO WORLD");'
