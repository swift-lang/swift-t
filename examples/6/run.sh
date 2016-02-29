#!/bin/sh
set -eu

export COMPILER=GNU
make
swift-t -r $PWD prog-swift.swift
