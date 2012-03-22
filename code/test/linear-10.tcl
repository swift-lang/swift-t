
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::set_file $d
}

proc rules { } {

    turbine::create_file 1 test/data/1.txt
    turbine::create_file 2 test/data/2.txt
    turbine::create_file 3 test/data/3.txt
    turbine::create_file 4 test/data/4.txt
    turbine::create_file 5 test/data/5.txt
    turbine::create_file 6 test/data/6.txt
    turbine::create_file 7 test/data/7.txt
    turbine::create_file 8 test/data/8.txt
    turbine::create_file 9 test/data/9.txt

    turbine::rule 1 {   } $turbine::WORK { function_touch 1 }
    turbine::rule 2 { 1 } $turbine::WORK { function_touch 2 }
    turbine::rule 3 { 2 } $turbine::WORK { function_touch 3 }
    turbine::rule 4 { 3 } $turbine::WORK { function_touch 4 }
    turbine::rule 5 { 4 } $turbine::WORK { function_touch 5 }
    turbine::rule 6 { 5 } $turbine::WORK { function_touch 6 }
    turbine::rule 7 { 6 } $turbine::WORK { function_touch 7 }
    turbine::rule 8 { 7 } $turbine::WORK { function_touch 8 }
    turbine::rule 9 { 8 } $turbine::WORK { function_touch 9 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
