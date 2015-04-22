> stc test-g-n.swift test-g-n.tcl

# Point Turbine to the Tcl package for g
> export TURBINE_USER_LIB=$PWD
# Turn off logging
> export TURBINE_LOG=0

# Single worker mode:
> time turbine test-g-n.tcl
g: 0+5=5
sleeping for 5 seconds...
g: 1+4=5
...
g: 5+0=5
sleeping for 5 seconds...
turbine test-g-n.tcl  30.60s user 2.26s system 108% cpu 30.300 total

# Many worker mode:
> time turbine -n 7 test-g-n.tcl
...
turbine -n 7 test-g-n.tcl  7.30s user 0.56s system 146% cpu 5.375 total
