
package require turbine 0.1

proc function_touch { d } {
  # exec touch $filename
    puts "function_touch: $d"
    turbine::file_set $d
}

proc rules { } {
    namespace import turbine::c::rule

    turbine::file_init 1 A.txt
    turbine::file_init 2 B.txt
    turbine::file_init 3 C.txt
    turbine::file_init 4 D.txt

    rule 1 A {     } { 1 } { tp: function_touch 1 }
    rule 2 B { 1   } { 2 } { tp: function_touch 2 }
    rule 3 C { 1   } { 3 } { tp: function_touch 3 }
    rule 4 D { 2 3 } { 4 } { tp: function_touch 4 }
}

turbine::init 1

turbine::start rules

turbine::finalize

puts OK
