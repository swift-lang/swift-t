#!/bin/sh -eu

echo "Build"
./build.sh

echo "Run"
swift-t -l -n 8 -r $PWD test-f.swift
