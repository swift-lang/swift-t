# Test garbage collection

package require turbine 0.0.1


turbine::defaults
turbine::init $engines $servers

proc test_insert_then_decr_ref {} {
  puts test_insert_then_decr_ref
  set C [ adlb::unique ]
  adlb::create $C $adlb::CONTAINER 0 integer

  set i1 [ adlb::unique ]
  adlb::create $i1 $adlb::INTEGER 0
  adlb::store $i1 $adlb::INTEGER 0
  adlb::insert $C 0 $i1

  set i2 [ adlb::unique ]
  adlb::create $i2 $adlb::INTEGER 0
  adlb::store $i2 $adlb::INTEGER 0
  # Insert and close
  adlb::insert $C 1 $i2 1

  set res [ adlb::lookup $C 0 ]
  if { $res != $i1 } {
    puts "res: $res != $i1"
    exit 1
  }

  if { ! [ adlb::exists $C ] } {
    puts "<$C> should exist"
    exit 1
  }

  adlb::refcount_incr $C $adlb::READ_REFCOUNT -1
  
  if { [ adlb::exists $C ] } {
    puts "<$C> should be destroyed"
    exit 1
  }
  # TODO: check that members destroyed
}

proc test_decr_ref_then_insert {} {
  puts test_decr_ref_then_insert 
  set C [ adlb::unique ]
  adlb::create $C $adlb::CONTAINER 0 integer

  set i1 [ adlb::unique ]
  adlb::create $i1 $adlb::INTEGER 0
  adlb::store $i1 $adlb::INTEGER 0
  adlb::insert $C 0 $i1

  # Take read refcount to 0
  adlb::refcount_incr $C $adlb::READ_REFCOUNT -1

  # Should still be able to insert since container write ref still > 0 
  set i2 [ adlb::unique ]
  adlb::create $i2 $adlb::INTEGER 0
  adlb::store $i2 $adlb::INTEGER 0
  # Insert 
  adlb::insert $C 1 $i2 1
  
  if { [ adlb::exists $C ] } {
    puts "<$C> should be destroyed"
    exit 1
  }
  # TODO: check that members destroyed
}

if { ! [ adlb::amserver ] } {
  test_insert_then_decr_ref
  test_decr_ref_then_insert
} else {
  adlb::server
}

turbine::finalize

puts OK
