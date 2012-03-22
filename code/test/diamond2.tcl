
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::set_file $d
}

proc rules { } {

    turbine::create_file 1 "test/data/A.txt"
    turbine::create_file 2 "test/data/B.txt"
    turbine::create_file 3 "test/data/C.txt"
    turbine::create_file 4 "test/data/D.txt"

    turbine::rule A {     } $turbine::WORK { function_touch 1 }
    turbine::rule B { 1   } $turbine::WORK { function_touch 2 }
    turbine::rule C { 1   } $turbine::WORK { function_touch 3 }
    turbine::rule D { 2 3 } $turbine::WORK { function_touch 4 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
