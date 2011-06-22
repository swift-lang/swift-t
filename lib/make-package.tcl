
# Generate the turbine TCL package

puts [ ::pkg::create -name turbine -version 0.1 -load libtclturbine.so -source turbine.tcl ]
