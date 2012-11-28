# Test garbage collection

package require turbine 0.0.1


turbine::defaults
turbine::init $engines $servers
turbine::enable_read_refcount

if { ! [ adlb::amserver ] } {
  set x [ adlb::unique ]
  adlb::create $x $adlb::INTEGER 0
  adlb::store $x $adlb::INTEGER 0
  if { ! [ adlb::exists $x ] } {
    puts "x does not exist after store"
    exit 1
  }

  # Set reference count to 0 to trigger destruction
  adlb::refcount_incr $x $adlb::READ_REFCOUNT -1
  if { [ adlb::exists $x ] } {
    puts "x exists after refcount 0"
    exit 1
  }

  set y [ adlb::unique ]
  adlb::create $y $adlb::INTEGER 0
  adlb::refcount_incr $y $adlb::READ_REFCOUNT -1
  # Set reference count to 0, but don't write: shouldn't be destroyed yet
  adlb::store $y $adlb::INTEGER 0
  if { [ adlb::exists $x ] } {
    puts "y exists after refcount 0"
    exit 1
  }

} else {
  adlb::server
}

turbine::finalize

puts OK
