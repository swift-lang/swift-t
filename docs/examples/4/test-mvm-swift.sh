#!/bin/bash

stc test-mvm.swift test-mvm.tcl

export TURBINE_USER_LIB=$PWD
turbine test-mvm.tcl
