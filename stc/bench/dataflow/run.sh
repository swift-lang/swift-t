#!/bin/bash

export TURBINE_ENGINES=1
export ADLB_SERVERS=1
PROCS=8

stc dataflow-1D.swift dataflow-1D.tcl

turbine -l -n ${PROCS} dataflow-1D.tcl

clog2_print adlb.clog2 > adlb.clog2.txt

grep metadata adlb.clog2.txt
