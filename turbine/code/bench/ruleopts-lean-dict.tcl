
package require turbine 0.1

set iterations 10000000

proc work {} {
  global iterations

  for { set i 0 } { $i < $iterations } { incr i } {
      # puts "iteration: $i"
      set L { dict create name tmp target 4 }
      set name [ dict get $L name ] 
      set target [ dict get $L target ] 
  }
}


set start [ clock milliseconds ]

work

set stop [ clock milliseconds ]

set duration [ expr $stop - $start ]
set seconds [ expr double($duration) / 1000 ]
puts "iterations: $iterations"
puts "duration: [ format %0.3f $seconds ]"
set rate [ expr $iterations / $seconds ]
puts "rate: [ format %0.0f $rate ]"
