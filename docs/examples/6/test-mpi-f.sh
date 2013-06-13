#!/bin/sh

stc -r $PWD test-mpi-f.swift test-mpi-f.tcl

turbine test-mpi-f.tcl

