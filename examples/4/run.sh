#!/bin/sh -eu

THIS=$( dirname $0 )
cd $THIS

echo "Stand-alone"
./test-mvm.sh

echo "Build"
./build.sh

echo "Run"
./test-mvm-swift.sh
