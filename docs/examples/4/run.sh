#!/bin/sh -eu

echo "Stand-alone"
./test-mvm.sh

echo "Build"
./build.sh

echo "Run"
./test-mvm-swift.sh
