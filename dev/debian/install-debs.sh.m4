#!/bin/sh
set -eu

# SWIFT/T: INSTALL DEBS

# This will install the 4 Swift/T modules as Debian packages

# First, install dependencies from APT
sudo apt-get install zsh tcl-dev mpich ant

# The correct version numbered are pasted here by M4:
CUTILS_VERSION=M4_CUTILS_VERSION
ADLBX_VERSION=M4_ADLBX_VERSION
TURBINE_VERSION=M4_TURBINE_VERSION
STC_VERSION=M4_STC_VERSION

# Ensure we are in the directory containing this script
cd $( dirname $0 )

# These Debian packages are in the swift-t-debs-*.tar.gz distribution
sudo dpkg -i exmcutils-dev_${CUTILS_VERSION}_amd64.deb
sudo dpkg -i adlbx-dev_${ADLBX_VERSION}_amd64.deb
sudo dpkg -i turbine_${TURBINE_VERSION}_amd64.deb
sudo dpkg -i stc_${STC_VERSION}_amd64.deb
