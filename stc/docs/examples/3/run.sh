#!/bin/sh -eu

turbine-write-doubles input.data 1 2 3 10
swift-t test-b-simple.swift
echo "Stand-alone build"
sh -e ./test-b-build.sh
echo "Stand-alone"
./b.x
echo "SWIG"
sh -e swig-b.sh
echo "Swift/T"
swift-t -r $PWD test-b.swift
