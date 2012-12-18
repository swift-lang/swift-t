
set items [ eval list \
                -source funcs.tcl     \
                -source 983.funcs.tcl ]

puts [ eval ::pkg::create -name "funcs" -version 0.0 $items ]
