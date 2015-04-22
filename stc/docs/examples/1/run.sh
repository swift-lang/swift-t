#!/bin/sh -e

cd $( dirname $0 )

tclsh make-package.tcl > pkgIndex.tcl

swift-t -r $PWD test-f.swift
