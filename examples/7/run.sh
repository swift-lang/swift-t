#!/bin/sh -eu

tclsh make-package.tcl > pkgIndex.tcl
./test-f.sh
