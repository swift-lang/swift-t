
# Generate the turbine TCL package

puts [ ::pkg::create -name turbine -version 0.1 -load libtcladlb.so -load libtclturbine.so -source turbine.tcl -source engine.tcl -source data.tcl -source turbine-lib.tcl -source helpers.tcl ]
