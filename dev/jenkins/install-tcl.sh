#!/bin/zsh
set -eu

# JENKINS INSTALL TCL

renice --priority 19 --pid $$ >& /dev/null

pwd
ls -l

# Clean up old stuff
rm -fv tcl8.6.12/README.md
rm -fr tcl8.6.12
rm -fv tcl8.6.12-src.tar.gz

# Download and extract
wget --no-verbose https://prdownloads.sourceforge.net/tcl/tcl8.6.12-src.tar.gz
tar xfz tcl8.6.12-src.tar.gz

# Build it!
cd tcl8.6.12/unix
./configure --prefix=$WORKSPACE/sfw/tcl-8.6.12
make clean
make binaries libraries
make install-binaries install-libraries install-headers
