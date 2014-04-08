set name     leaf_main
set version  0.0
set leaf_so  userlib.so
set leaf_tcl user-leaf.tcl

puts [ ::pkg::create -name $name -version $version            -load $leaf_so -source $leaf_tcl ]

