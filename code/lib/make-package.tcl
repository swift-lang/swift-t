
# Generate the Turbine Tcl package

set turbine_version $env(TURBINE_VERSION)

# List of Turbine shared objects and Tcl libraries
# Must be kept in sync with list in lib/module.mk.in
puts [ ::pkg::create -name turbine -version $turbine_version \
       -load libtcladlb.so -load libtclturbine.so \
       -source turbine.tcl   \
       -source engine.tcl    \
       -source data.tcl      \
       -source functions.tcl \
       -source assert.tcl \
       -source logical.tcl \
       -source string.tcl \
       -source arith.tcl \
       -source container.tcl \
       -source rand.tcl      \
       -source stdio.tcl      \
       -source updateable.tcl      \
       -source unistd.tcl     \
       -source helpers.tcl ]
