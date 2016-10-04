#!/bin/sh
set -eu

# SWIFT/T: INSTALL DEBS

# This will install the 4 Swift/T modules as Debian packages

# First, install dependencies from APT
sudo apt-get install zsh tcl-dev mpich ant

# The correct version numbered are pasted here:
CUTILS_VERSION=M4_CUTILS_VERSION
ADLBX_VERSION=M4_ADLBX_VERSION
TURBINE_VERSION=M4_TURBINE_VERSION
STC_VERSION=M4_STC_VERSION

# These Debian packages are in the swift-t-debs-*.tar.gz distribution
sudo dpkg -i exmcutils-dev_${CUTILS_VERSION}-1.*.deb
sudo dpkg -i adlbx-dev_${ADLBX_VERSION}-1.*.deb
sudo dpkg -i turbine-dev_${TURBINE_VERSION}-1.*.deb
sudo dpkg -i stc-dev_${STC_VERSION}-1.*.deb
