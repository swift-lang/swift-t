# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# Flex ADLB data store with Turbine data
# No real Turbine data flow here

# This may be used as a benchmark by setting
# TURBINE_TEST_PARAM_1 in the environment

package require turbine 1.0

namespace import turbine::string_*

set iterations 10

turbine::defaults
turbine::init $servers
turbine::enable_read_refcount

global TYPE_A TYPE_B TYPENAME_A TYPENAME_B
set TYPE_A 1
set TYPENAME_A struct:A
set TYPE_B 2
set TYPENAME_B struct:B

adlb::declare_struct_type $TYPE_A $TYPENAME_A [ list a integer b string c float ]
puts "Declared struct type $TYPE_A"
adlb::declare_struct_type $TYPE_B $TYPENAME_B [ list a integer b.one string \
                                            b.two float c.three.a ref ]
puts "Declared struct type $TYPE_B"

# Check double declare caught
if { ! [ catch { adlb::declare_struct_type 1234 $TYPENAME_B [ list ] } ] } {
  error "Didn't catch double declare of struct type"
}


proc check_a { act exp int_val str_val float_val } {
  if { [ dict get $act a ] != $int_val ||
       ! [ string equal [ dict get $act b ] $str_val ] ||
       [ dict get $act c ] != $float_val } {
    error "Dict $act does not match expected $exp"
    exit 1
  }
}

proc check_b { act exp int_val str_val float_val ref } {
  if { [ dict get $act a ] != $int_val ||
       ! [ string equal [ dict get $act b one ] $str_val ] ||
       [ dict get $act b two ] != $float_val ||
       [ dict get $act c three a ] != $ref } {
    error "Dict $act does not match expected $exp"
    exit 1
  }
}

proc do_test { i } {
    global TYPE_A TYPE_B TYPENAME_A TYPENAME_B
    # Testing first type
    set id [ adlb::create $::adlb::NULL_ID struct ]
    puts "Created <$id>"

    set int_val $i
    set str_val "string-$i"
    set float_val [ expr 3.14 * $i ]

    set v [ dict create a $int_val b $str_val c $float_val ]
    adlb::store $id struct$TYPE_A $v
    puts "Stored <$id>=$v"

    set v2 [ adlb::retrieve $id struct ]
    puts "Retrieved <$id>=$v2"

    check_a $v2 $v $int_val $str_val $float_val

    # Testing second type with nested structs
    set id_2 [ adlb::create $::adlb::NULL_ID $TYPENAME_B ]
    puts "Created <$id_2>"

    set int_val $i
    set str_val "string-$i"
    set float_val [ expr 3.14 * $i ]
    set ref $id

    set v [ dict create a $int_val \
            b [ dict create one $str_val two $float_val ] \
            c [ dict create three [ dict create a $ref ] ] ]


    # Make sure the refcount of <$id> is two
    adlb::refcount_incr $id r 1
    
    # Check for error when we don't specify struct subtype
    if { ! [ catch {adlb::store $id_2 struct $v} ] } {
      error "Expected error when not specifying subtype"
    }

    adlb::store $id_2 $TYPENAME_B $v
    puts "Stored <$id_2>=$v"
    
    # Check for error when we don't specify struct subtype
    if { ! [ catch { adlb::retrieve $id_2 struct$TYPE_A } ] } {
      error "Expected error when specifying wrong struct subtype"
    }

    set v2 [ adlb::retrieve_decr $id_2 1 $TYPENAME_B ]
    puts "Retrieved <$id_2>=$v2"
    check_b $v2 $v $int_val $str_val $float_val $ref

    # <$id_2> should have been cleaned up by retrieve decrement
    if [ adlb::exists $id_2 ] {
      error "Didn't clean up <$id_2>"
    }
    # But <$id> should still exist, but with refcount decremented
    if { ! [ adlb::exists $id ] } {
      error "<$id> should not be cleaned up yet"
    }

    # Check that structs work ok as container values
    set c [ adlb::create $::adlb::NULL_ID container integer struct ]
    adlb::insert $c 0 $v2 struct$TYPE_B
    puts "Stored <$c>\[0\]=$v2"

    set v3 [ adlb::lookup $c 0 ]
    puts "Retrieved <$c>\[0\]=$v3"
    check_b $v3 $v $int_val $str_val $float_val $ref

    # To be fiendish, insert_atomic without followup, to see what happens
    if { ! [ adlb::insert_atomic $c 1 ] } {
        error "$c\[1\] shouldn't exist"
    }

    # Free container.  This should also free <$id>
    adlb::refcount_incr $c rw -1
    if [ adlb::exists $c ] {
      error "Didn't clean up <$c>"
    }
    if [ adlb::exists $id ] {
      error "Didn't clean up <$id>"
    }


    # Test invalid struct type store
    set bad_id [ adlb::create $::adlb::NULL_ID struct ]
    if { ! [ catch { adlb::store $bad_id struct$TYPE_A $v } ] } {
        error "Expected error storing <$bad_id>=$v  with struct type $TYPE_A"
    }
    adlb::refcount_incr $bad_id rw -1
}

if { ! [ adlb::amserver ] } {

    set rank [ adlb::comm_rank ]
    puts "rank: $rank"
    set workers [ adlb::workers ]
    puts "workers:    $workers"

    for { set i 1 } { $i <= $iterations } { incr i } {
        do_test $i
    }
} else {
    adlb::server
}

turbine::finalize

puts OK
