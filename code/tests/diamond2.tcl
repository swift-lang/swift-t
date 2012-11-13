
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::store_file $d
}

proc rules { } {

    turbine::create_file 101 0 "tests/data/A.txt"
    turbine::create_file 102 0 "tests/data/B.txt"
    turbine::create_file 103 0 "tests/data/C.txt"
    turbine::create_file 104 0 "tests/data/D.txt"

    turbine::rule A {     } $turbine::WORK { function_touch 101 }
    turbine::rule B { 101 } $turbine::WORK { function_touch 102 }
    turbine::rule C { 101 } $turbine::WORK { function_touch 103 }
    turbine::rule D { 102 103 } $turbine::WORK { function_touch 104 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
