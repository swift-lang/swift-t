
set name     power_main
set version  0.0
set leaf_so  libpower_main.so
set leaf_tcl power-leaf.tcl

puts [ ::pkg::create -name $name -version $version \
           -load $leaf_so -source $leaf_tcl ]
