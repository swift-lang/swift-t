
load lib/libtclturbine.so

turbine_init

turbine_file 0 /dev/null
turbine_file 1 A.txt
turbine_file 2 B.txt
turbine_file 3 C.txt
turbine_file 4 D.txt

turbine_rule 1 A { 0 } { 1 } { touch A.txt }

turbine_push

turbine_finalize

puts OK
