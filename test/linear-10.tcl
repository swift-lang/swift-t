package require turbine 0.1
turbine_init

turbine_file 1 test/data/1.txt
turbine_file 2 test/data/2.txt
turbine_file 3 test/data/3.txt
turbine_file 4 test/data/4.txt
turbine_file 5 test/data/5.txt
turbine_file 6 test/data/6.txt
turbine_file 7 test/data/7.txt
turbine_file 8 test/data/8.txt
turbine_file 9 test/data/9.txt

turbine_rule 1 1 {   } { 1 } { touch test/data/1.txt }
turbine_rule 2 2 { 1 } { 2 } { touch test/data/2.txt }
turbine_rule 3 3 { 2 } { 3 } { touch test/data/3.txt }
turbine_rule 4 4 { 3 } { 4 } { touch test/data/4.txt }
turbine_rule 5 5 { 4 } { 5 } { touch test/data/5.txt }
turbine_rule 6 6 { 5 } { 6 } { touch test/data/6.txt }
turbine_rule 7 7 { 6 } { 7 } { touch test/data/7.txt }
turbine_rule 8 8 { 7 } { 8 } { touch test/data/8.txt }
turbine_rule 9 9 { 8 } { 9 } { touch test/data/9.txt }

turbine_engine
turbine_finalize
puts OK
