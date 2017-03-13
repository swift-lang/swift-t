#!/bin/bash
set -eu

# BUILD-SPACKS

../../dev/debian/mk-upstream-tgz.sh spack exmcutils-0.5.3.tar.gz exmcutils 0.5.3 $PWD/maint/file-list.zsh

../../dev/debian/mk-upstream-tgz.sh spack adlbx-0.8.0.tar.gz adlbx 0.8.0 $PWD/maint/file-list.zsh

../../dev/debian/mk-upstream-tgz.sh spack turbine-1.0.0.tar.gz turbine 1.0.0 $PWD/maint/file-list.zsh

../../dev/debian/mk-upstream-tgz.sh spack stc-0.7.3.tar.gz stc 0.7.3 $PWD/maint/file-list.zsh
