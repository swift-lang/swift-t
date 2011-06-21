
load lib/libtclturbine.so

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

while {true} {

    turbine_push

    set ready [ turbine_ready ]
    if { ! [ string length $ready ] } break

    foreach {transform} $ready {
        set command [ turbine_executor $transform ]
        puts "executing: $command"
        if { [ catch { eval exec $command } ] } {
            error "rule: $transform failed in command: $command"
        }
        turbine_complete $transform
    }
}

turbine_finalize

puts OK
