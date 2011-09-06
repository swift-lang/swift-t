
# Simple diamond test case but use ADLB

package require turbine 0.1

proc rules { } {
    turbine::c::file 0 /dev/null
    turbine::c::file 1 A.txt
    turbine::c::file 2 B.txt
    turbine::c::file 3 C.txt
    turbine::c::file 4 D.txt

    turbine::c::rule 1 A {     } { 1 } { touch test/data/A.txt }
    turbine::c::rule 2 B { 1   } { 2 } { touch test/data/B.txt }
    turbine::c::rule 3 C { 1   } { 3 } { touch test/data/C.txt }
    turbine::c::rule 4 D { 2 3 } { 4 } { touch test/data/D.txt }
}

namespace import turbine::adlb::*
init
start rules
finalize

puts OK
