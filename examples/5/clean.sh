#!/bin/bash
set -e

# Do not delete main_leaf.c, it is pasted into the Leaf Guide.

rm -f main_leaf.{h,o,tcl} extension.*
rm -f make-package.tcl pkgIndex.tcl
rm -f *.tic *.o *.so *.x
rm -f user-code.swift
