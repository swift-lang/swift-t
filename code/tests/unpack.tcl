# Test container unpacking

package require turbine 0.0.1
namespace import turbine::*

proc main { } {
  allocate_container C integer
  allocate x1 integer 0
  allocate x2 string 0
  allocate x3 float 0
  allocate x4 string 0
  container_insert $C 1 $x1
  container_insert $C 4 $x2
  container_insert $C 8 $x3
  container_insert $C 12 $x4
  store_integer $x1 1234
  store_string $x2 "word"
  store_float $x3 3.14
  store_string $x4 "quick brown fox"

  set res [ unpack_args $C 0 0 ]
  puts "res: $res"
  if { [ llength $res ] != 4 } {
    error "length of res wrong"
  }
  if { [ lindex $res 0 ] != 1234 } {
    error {C[1]}
  }
  if { ! [ string equal [ lindex $res 1 ] "word" ] } {
    error {C[4]}
  }
  if { [ lindex $res 2 ] != 3.14 } {
    error {C[8]}
  }
  if { ! [ string equal [ lindex $res 3 ] "quick brown fox" ] } {
    error {C[12]}
  }
}

turbine::defaults
turbine::init $engines $servers
turbine::start main
turbine::finalize

puts OK

# Help Tcl free memory
proc exit args {}
