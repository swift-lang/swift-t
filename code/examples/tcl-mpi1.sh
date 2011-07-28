#!/bin/sh

set -x 
{
  echo MPI
  /home/wozniak/sfw/tcl-8.4.19/bin/tclsh8.4 /home/wozniak/exm/turbine/examples/tcl-mpi1.tcl 
} >> /home/wozniak/output.txt 2>&1
