
load lib/libtclturbine.so

puts OK

turbine_init

turbine_file 1 A.txt
turbine_file 2 B.txt
turbine_file 3 C.txt
turbine_file 4 D.txt

turbine_finalize
