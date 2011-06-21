
load lib/libtclturbine.so

source tcl/turbine.tcl

turbine_init

turbine_file 0 /dev/null
turbine_file 1 A.txt
turbine_file 2 B.txt
turbine_file 3 C.txt
turbine_file 4 D.txt

turbine_rule 1 A {     } { 1 } { touch A.txt }
turbine_rule 2 B { 1   } { 2 } { touch B.txt }
turbine_rule 3 C { 1   } { 3 } { touch C.txt }
turbine_rule 4 D { 2 3 } { 4 } { touch D.txt }

turbine_engine

turbine_finalize

puts OK
