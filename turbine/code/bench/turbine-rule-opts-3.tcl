
package require turbine 0.1

set iterations 10000
set range 10
set retrieves 3

proc f { d } {
    global range retrieves
    for { set i 0 } { $i < $retrieves } { incr i } {
        set c [ expr floor( rand() * $range ) ]
        # if [ dict exists $d $c ] {
        #     set v [ dict get $d $c ]
        #     # puts "v: $v"
        # }
    }
}

set start [ clock milliseconds ]

for { set i 0 } { $i < $iterations } { incr i } {
    # puts "iteration: $i"
    set L { name=tmp target=4 }
    for { set j 0 } { $j < $range } { incr j } {
        if { floor(rand()*2) == 0 } {
            set k [ expr floor( rand() * $range ) ]
            # puts "store $k"
            # dict set d $k $k
        }
    }
    f $L
    turbine::c::ruleopts $L
}

set stop [ clock milliseconds ]

set duration [ expr $stop - $start ]
set seconds [ expr double($duration) / 1000 ]
puts "iterations: $iterations"
puts "duration: [ format %0.3f $seconds ]"
set rate [ expr $iterations / $seconds ]
puts "rate: [ format %0.0f $rate ]"
