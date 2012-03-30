
set name     "bench"
set version  "0.0.1"

puts [ ::pkg::create -name $name -version $version \
           -source "bench.tcl" ]
