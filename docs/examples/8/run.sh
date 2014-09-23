#!/bin/sh -eu

echo "Build"
./build.sh

echo "Run"
swift-t -n 4 -r $PWD test-f.swift
