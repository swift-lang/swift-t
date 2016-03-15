#!/bin/sh -ex

./build.sh

turbine -n 4 test-f.tic
