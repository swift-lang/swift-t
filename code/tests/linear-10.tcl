
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::store_file $d
}

proc rules { } {

    turbine::create_file 11 tests/data/1.txt
    turbine::create_file 12 tests/data/2.txt
    turbine::create_file 13 tests/data/3.txt
    turbine::create_file 14 tests/data/4.txt
    turbine::create_file 15 tests/data/5.txt
    turbine::create_file 16 tests/data/6.txt
    turbine::create_file 17 tests/data/7.txt
    turbine::create_file 18 tests/data/8.txt
    turbine::create_file 19 tests/data/9.txt

    turbine::rule 1 {   }  $turbine::WORK { function_touch 11 }
    turbine::rule 2 { 11 } $turbine::WORK { function_touch 12 }
    turbine::rule 3 { 12 } $turbine::WORK { function_touch 13 }
    turbine::rule 4 { 13 } $turbine::WORK { function_touch 14 }
    turbine::rule 5 { 14 } $turbine::WORK { function_touch 15 }
    turbine::rule 6 { 15 } $turbine::WORK { function_touch 16 }
    turbine::rule 7 { 16 } $turbine::WORK { function_touch 17 }
    turbine::rule 8 { 17 } $turbine::WORK { function_touch 18 }
    turbine::rule 9 { 18 } $turbine::WORK { function_touch 19 }
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK
