
set name     f
set version  0.0
set leaf_so  libfunc.so
set leaf_tcl func.tcl

puts [ ::pkg::create -name $name -version $version \
           -load $leaf_so -source $leaf_tcl ]
