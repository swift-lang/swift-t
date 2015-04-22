
set items [ eval list \
                -source funcs.tcl     \
                -source 983.funcs.tcl ]

puts [ eval ::pkg::create -name "funcs" -version 0.0 $items ]

set items [ eval list \
                -source 165-autowrap-struct.funcs.tcl ]
puts [ eval ::pkg::create -name "funcs_165" -version 0.5 $items ]

set items [ eval list \
                -source 5697-url.funcs.tcl ]
puts [ eval ::pkg::create -name "funcs_5697" -version 0.5 $items ]

set items [ eval list \
                -source 986-sudoku.func.tcl ]
puts [ eval ::pkg::create -name "sudoku" -version 0.0 $items ]

set items [ eval list \
                -source 654-typevar.funcs.tcl ]
puts [ eval ::pkg::create -name "funcs_654" -version 2.0 $items ]
