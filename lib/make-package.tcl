
# Generate the turbine TCL package

puts [ ::pkg::create -name turbine -version 0.1 -load libtcladlb.so -load libtclturbine.so -source turbine.tcl -source turbine-engine.tcl -source data.tcl -source adlb-data.tcl -source turbine-engine-adlb.tcl -source turbine-lib.tcl -source helpers.tcl ]
