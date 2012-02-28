
# Generate the Turbine Tcl package

set turbine_version $env(TURBINE_VERSION)

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
       -source lang.tcl      \
       -source helpers.tcl ]
