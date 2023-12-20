#!/bin/bash
set -eux

# JENKINS TCL

renice --priority 19 --pid $$
rm -rfv downloads
rm -fv tcl8.6.12-src.tar.gz
wget --no-verbose https://prdownloads.sourceforge.net/tcl/tcl8.6.12-src.tar.gz
tar xfz tcl8.6.12-src.tar.gz
cd tcl8.6.12/unix
./configure --prefix=$WORKSPACE/sfw/tcl-8.6.12
make binaries libraries
make install-binaries install-libraries install-headers
