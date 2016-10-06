#!/bin/sh
set -e

# DEBIAN CONFIGURE ALL
# Configure everything to make Debian packages

dev/build/rebuild-all.sh -c -m
cd stc/code
autoconf
./configure -q
