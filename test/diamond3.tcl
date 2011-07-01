
# Simple diamond test case but use ADLB

package require turbine 0.1

proc rules { } {
    turbine_file 0 /dev/null
    turbine_file 1 A.txt
    turbine_file 2 B.txt
    turbine_file 3 C.txt
    turbine_file 4 D.txt

    turbine_rule 1 A {     } { 1 } { touch test/data/A.txt }
    turbine_rule 2 B { 1   } { 2 } { touch test/data/B.txt }
    turbine_rule 3 C { 1   } { 3 } { touch test/data/C.txt }
    turbine_rule 4 D { 2 3 } { 4 } { touch test/data/D.txt }
}

turbine_init
adlb_init

turbine_adlb rules

turbine_finalize
adlb_finalize

puts OK
