#!/bin/sh
set -eu

echo "Build"
./build.sh

echo "Run"
swift-t -l -n 4 -r $PWD test-f.swift
