> stc test-g-1.swift test-g-1.tcl

# Point Turbine to the Tcl package for g
> export TURBINE_USER_LIB=$PWD
# Turn off logging
> export TURBINE_LOG=0

> time turbine test-g-1.tcl
turbine test-g-1.tcl  5.60s user 0.44s system 113% cpu 5.300 total
