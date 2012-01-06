
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::file_set $d
}

proc rules { } {

    namespace import turbine::c::rule
    namespace import turbine::file_init

    file_init 1 "test/data/A.txt"
    file_init 2 "test/data/B.txt"
    file_init 3 "test/data/C.txt"
    file_init 4 "test/data/D.txt"

    rule 1 A {     } { 1 } { tf: function_touch 1 }
    rule 2 B { 1   } { 2 } { tf: function_touch 2 }
    rule 3 C { 1   } { 3 } { tf: function_touch 3 }
    rule 4 D { 2 3 } { 4 } { tf: function_touch 4 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
