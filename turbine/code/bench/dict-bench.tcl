
set iterations 2000000
set range 10
set retrieves 3

proc f { d } {
    global range retrieves
    for { set i 0 } { $i < $retrieves } { incr i } {
        set c [ expr {floor( rand() * $range )} ]
        if [ dict exists $d $c ] {
            set v [ dict get $d $c ]
            # puts "v: $v"
        }
    }
}
proc test {} {
  global iterations range retrieves
  for { set i 0 } { $i < $iterations } { incr i } {
      # puts "iteration: $i"
      set d [ dict create ]
      for { set j 0 } { $j < $range } { incr j } {
          if { [ expr {floor(rand()*2) == 0} ] } {
              set k [ expr {floor( rand() * $range )} ]
              # puts "store $k"
              dict set d $k $k
          }
      }
      f $d
  }
}

set start [ clock milliseconds ]
test
set stop [ clock milliseconds ]

set duration [ expr $stop - $start ]
set seconds [ expr double($duration) / 1000 ]
puts "iterations: $iterations"
puts "duration: [ format %0.3f $seconds ]"
set rate [ expr $iterations / $seconds ]
puts "rate: [ format %0.0f $rate ]"

