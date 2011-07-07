
Examples to run JETS+TCL+ADLB+Turbine

Currently works on surveyor. 

First, run jets/examples/sites/surveyor/start-mpi-sleep.zsh 

Each test consists of a JETS input file, a wrapper script called by
that file, and an underlying TCL script.

Then, run jets <opts> <test>.jets
where <opts> is "-r mpi" if you need MPI.

You will need to modify paths in the scripts to get them to work for
you: these are examples for ExM discussion, not end-users.  (We could
fix this by using PATH or something...)

Tests:

tcl1: Just see if TCL works

tcl-mpi1: See if TCL works as an MPI job

adlb-noop: Try to run the adlb-noop test 
