#!/bin/sh
set -eu

echo "Stand-alone C program"
./test-g.sh

echo
echo "Run SWIG"
swig -tcl g.i

echo
echo "Compile SWIG-generated module"
./compile-swig-g.sh

echo
echo "TEST-G-TCL"
export TCLLIBPATH=$PWD
tclsh test-g.tcl

echo
echo "TEST-G-SWIFT"
swift-t -r $PWD test-g-1.swift
