
set items [ eval list \
                -source funcs.tcl     \
                -source 983.funcs.tcl ]

puts [ eval ::pkg::create -name "funcs" -version 0.0 $items ]

set items [ eval list \
                -source 165-autowrap-struct.funcs.tcl ]
puts [ eval ::pkg::create -name "funcs_165" -version 0.5 $items ]
