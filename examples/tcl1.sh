#!/bin/sh

set -x 
{
  /home/wozniak/sfw/tcl-8.4.19/bin/tclsh8.4 /home/wozniak/exm/turbine/examples/tcl1.tcl 
} >> /home/wozniak/output.txt 2>&1
