package require turbine 0.1
turbine_init

turbine_file 0 0.txt
turbine_file 1 1.txt
turbine_file 2 2.txt
turbine_file 3 3.txt
turbine_file 4 4.txt
turbine_file 5 5.txt
turbine_file 6 6.txt
turbine_file 7 7.txt
turbine_file 8 8.txt
turbine_file 9 9.txt

turbine_rule 0 0 { } { 0 } { touch 0.txt }
turbine_rule 1 1 { 0 } { 1 } { touch 1.txt }
turbine_rule 2 2 { 1 } { 2 } { touch 2.txt }
turbine_rule 3 3 { 2 } { 3 } { touch 3.txt }
turbine_rule 4 4 { 3 } { 4 } { touch 4.txt }
turbine_rule 5 5 { 4 } { 5 } { touch 5.txt }
turbine_rule 6 6 { 5 } { 6 } { touch 6.txt }
turbine_rule 7 7 { 6 } { 7 } { touch 7.txt }
turbine_rule 8 8 { 7 } { 8 } { touch 8.txt }
turbine_rule 9 9 { 8 } { 9 } { touch 9.txt }

turbine_engine
turbine_finalize
puts OK
