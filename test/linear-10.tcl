
package require turbine 0.1

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

    file_init 1 test/data/1.txt
    file_init 2 test/data/2.txt
    file_init 3 test/data/3.txt
    file_init 4 test/data/4.txt
    file_init 5 test/data/5.txt
    file_init 6 test/data/6.txt
    file_init 7 test/data/7.txt
    file_init 8 test/data/8.txt
    file_init 9 test/data/9.txt

    rule 1 1 {   } { 1 } { tf: function_touch 1 }
    rule 2 2 { 1 } { 2 } { tf: function_touch 2 }
    rule 3 3 { 2 } { 3 } { tf: function_touch 3 }
    rule 4 4 { 3 } { 4 } { tf: function_touch 4 }
    rule 5 5 { 4 } { 5 } { tf: function_touch 5 }
    rule 6 6 { 5 } { 6 } { tf: function_touch 6 }
    rule 7 7 { 6 } { 7 } { tf: function_touch 7 }
    rule 8 8 { 7 } { 8 } { tf: function_touch 8 }
    rule 9 9 { 8 } { 9 } { tf: function_touch 9 }
}

turbine::init $env(TURBINE_ENGINES) $env(ADLB_SERVERS)

turbine::start rules

turbine::finalize

puts OK
