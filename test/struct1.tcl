
package require turbine 0.1

# file A;
# A = touch();
# type s1 { file p; file q }
# s1 B;
# B.p = cp(A);
# B.q = cp(A);

proc f { src dest } {
    set file1 [ turbine_filename $src ]
    set file2 [ turbine_filename $dest ]
    puts "cp $file1 $file2"
}

proc rules { } {
    turbine_file 0 /dev/null
    turbine_file 1 A.txt
    turbine_container 2

    set v1 [ turbine_new ]
    puts "v1: $v1"
    turbine_file $v1 B.p.txt
    turbine_insert 2 field p $v1

    set v2 [ turbine_new ]
    puts "v2: $v2"
    turbine_file $v2 B.q.txt
    turbine_insert 2 field q $v2

    turbine_rule 1 A  { } 1   { touch A.txt }
    turbine_rule 2 F1 1   $v1 "tp: f 1 $v1"
    turbine_rule 3 F2 1   $v2 "tp: f 1 $v2"
}

turbine_init

rules

turbine_engine

turbine_finalize

puts OK
