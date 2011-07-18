
package require turbine 0.1

proc rules { } {
    namespace import turbine::c::rule

    turbine::c::file 1 A.txt
    turbine::c::file 2 B.txt
    turbine::c::file 3 C.txt
    turbine::c::file 4 D.txt

    rule 1 A { }  { 1 } { touch test/data/A.txt }
    rule 2 B { 1   } { 2 } { touch test/data/B.txt }
    rule 3 C { 1   } { 3 } { touch test/data/C.txt }
    rule 4 D { 2 3 } { 4 } { touch test/data/D.txt }
}

turbine::init

rules

turbine::engine

turbine::finalize

puts OK
