
package require turbine 0.0.1

proc function_touch { d } {

    puts "function_touch: $d"
    set filename [ turbine::filename $d ]
    puts "function_touch: filename: $filename"
    exec touch $filename
    turbine::set_file $d
}

proc rules { } {

    turbine::create_file 11 test/data/1.txt
    turbine::create_file 12 test/data/2.txt
    turbine::create_file 13 test/data/3.txt
    turbine::create_file 14 test/data/4.txt
    turbine::create_file 15 test/data/5.txt
    turbine::create_file 16 test/data/6.txt
    turbine::create_file 17 test/data/7.txt
    turbine::create_file 18 test/data/8.txt
    turbine::create_file 19 test/data/9.txt

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
