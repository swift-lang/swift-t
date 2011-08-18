
# Generate the Turbine TCL package

puts [ ::pkg::create -name turbine -version 0.1 \
       -load libtcladlb.so -load libtclturbine.so \
       -source turbine.tcl   \
       -source engine.tcl    \
       -source data.tcl      \
       -source functions.tcl \
       -source lang.tcl      \
       -source helpers.tcl ]
