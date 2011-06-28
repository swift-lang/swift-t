
package require turbine 0.1

# file A;
# A = touch();
# type s1 { file p; file q }
# s1 B;
# B.p = cp(A);
# B.q = cp(A);

proc rules { } {
    turbine_file 0 /dev/null
    turbine_file 1 A.txt
    turbine_container 2
    set v1 [ turbine_lookup 2 field p ]
    turbine_file $v1 B.p.txt
    set v2 [ turbine_lookup 2 field q ]
    turbine_file $v2 B.q.txt

    turbine_rule 1 A  {   } { 1 } { touch A.txt }
    turbine_rule 2 F1 { 1 } { 2 } { tp: f { 1 } { 2 } }
}

turbine_init

rules

turbine_engine

turbine_finalize

puts OK
