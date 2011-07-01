package require turbine 0.1
turbine_init

turbine_file 0 0.txt
turbine_file 1 1.txt
turbine_file 2 2.txt

turbine_rule 0 0 { } { 0 } { touch 0.txt }
turbine_rule 1 1 { 0 } { 1 } { touch 1.txt }
turbine_rule 2 2 { 1 } { 2 } { touch 2.txt }

turbine_engine
turbine_finalize
puts OK
