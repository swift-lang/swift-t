# Test for id/subscript rule

package require turbine 1.0
namespace import turbine::*

# Check exists
proc one_closed { C i } {
  set v [ container_lookup $C $i ]
  if { [ string length $v ] == 0 } {
    error "$C\[$i\] not found"
  }
}


# Should run after all closed
proc all_closed { C id } {
  set contents [ retrieve $C ]
  puts "all_closed($id) C=<$C>=$contents"

  if { [ dict size $contents] != 3 } {
    error "$id: Expected 3 entries: $contents"
  }
  if { ! [ string equal [ dict get $contents 1 ] "quick" ] || \
       ! [ string equal [ dict get $contents 2 ] "brown" ] || \
       ! [ string equal [ dict get $contents 3 ] "fox" ] } {
    error "$id: Dictionary contents not as expected: $contents"
  }
    
}

proc main {  } {
    allocate_container C integer string
    container_insert $C 3 "fox" string

    # Check that rules fire after container entries closed
    turbine::rule [ list "$C.3" ] "one_closed $C 3" \
                  type $turbine::CONTROL

    turbine::rule [ list "$C.2" ] "one_closed $C 2" \
                  type $turbine::CONTROL
    
    turbine::rule [ list "$C.1" ] "one_closed $C 1" \
                  type $turbine::CONTROL

    # Wait on both
    turbine::rule [ list $C "$C.3" ] "all_closed $C A" \
                  type $turbine::CONTROL

    # Wait on just ID
    turbine::rule [ list $C ] "all_closed $C B" type $turbine::CONTROL
    
    # Wait on all
    turbine::rule [ list "$C.1" "$C.3" "$C.2" $C ] \
                    "all_closed $C C" type $turbine::CONTROL

    # Deferred insert
    turbine::rule [ list "$C.1" ] "container_insert $C 2 \"brown\" string ; \
                                adlb::write_refcount_decr $C" \
                                type $turbine::CONTROL
    container_insert $C 1 "quick" string
    
    
    # Also check that rules fire when container assigned all at once
    allocate_container C2 integer string
    turbine::rule [ list "$C2.0" ] "one_closed $C2 0" \
                  type $turbine::CONTROL
    
    turbine::array_build $C2 [ list one two ] 1 string
    
    turbine::rule [ list "$C2.1" ] "one_closed $C2 1" \
                  type $turbine::CONTROL
}

proc echo { stack args } {
    turbine::c::log [ list exec: /bin/echo {*}[ turbine::unpack_args ${args} 1 0 ] [ dict create ] ]
    turbine::exec_external "/bin/echo" [ dict create ] "echo:" {*}[ turbine::unpack_args ${args} 1 0 ]
    turbine::read_refcount_decr ${args} 1
}

turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start main
turbine::finalize


