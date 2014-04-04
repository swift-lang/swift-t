
set name     thfribo_main
set version  0.0
set leaf_so  libthfribo_main.so
set leaf_tcl thfribo-main.tcl

puts [ ::pkg::create -name $name -version $version \
           -load $leaf_so -source $leaf_tcl ]
