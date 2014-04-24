#!/bin/sh

stc -r $PWD test-f.swift test-f.tcl || exit 1
turbine test-f.tcl
