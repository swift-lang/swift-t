
set name     swift_main
set version  0.0
set leaf_so  libswift_main.so
set leaf_tcl swift-main.tcl

puts [ ::pkg::create -name $name -version $version \
           -load $leaf_so -source $leaf_tcl ]
