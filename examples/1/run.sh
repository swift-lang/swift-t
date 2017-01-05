#!/bin/sh
set -e

set -x

cd $( dirname $0 )

tclsh make-package.tcl > pkgIndex.tcl

swift-t -r $PWD test-f.swift
