
# JSON TCL
# Special features for handling JSON

namespace eval turbine {

  proc json_encode_object { result inputs } {
    set names [ list_pop_first inputs ]
    rule $inputs "json_encode_object_body $result $names $inputs" \
        name "json_encode_object-$result"
  }
  proc json_encode_object_body { result names args } {
    set L [ list ]
    # A dict (index->"name"):
    set D [ adlb::enumerate $names dict all 0 1 ]

    json_encode_check_count $D $args "json_encode_object" "names"

    set i 0
    foreach a $args {
      set name  [ dict get $D $i ]
      set value [ retrieve_decr $a ]
      set type  [ adlb::typeof  $a ]
      lappend L [ list $name $value $type ]
      incr i
    }
    set s [ json_encode_impl {*}$L ]

    store_string $result $s
  }

  # Encode into comma separated list, possibly with "name": prefixes
  proc json_encode_impl { args } {
    # List of key-values
    set kvs [ list ]
    # nvt: name+value+type (strings), name may be empty for JSON array
    foreach nvt $args {
      lassign $nvt name value type
      set token [ json_prefix $name $value ]
      if { $type eq "float" || $type eq "integer" } {
        append token $value
      } elseif { $type eq "string" } {
        append token "\"$value\""
      }
      lappend kvs $token
    }
    set result [ join $kvs ", " ]
    return $result
  }

  proc json_prefix { name value } {
    if { $name eq "" } {
      # JSON array
      set token ""
    } else {
      # JSON object
      set token "\"$name\":"
    }
    return $token
  }

  proc json_encode_object_retype { result inputs } {
    set names [ list_pop_first inputs ]
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_object_retype_body $result $names $types $inputs" \
        name "json_encode_object_retype-$result"
  }
  proc json_encode_object_retype_body { result names types args } {
    set L [ list ]
    # A dict (index->"name"):
    set D_names [ adlb::enumerate $names dict all 0 1 ]
    set D_types [ adlb::enumerate $types dict all 0 1 ]

    # show D_names D_types

    json_encode_check_count $D_names $args "json_encode_object_retype" "names"
    json_encode_check_count $D_types $args "json_encode_object_retype" "types"

    set i 0
    foreach a $args {
      set name  [ dict get $D_names $i ]
      set type  [ dict get $D_types $i ]
      set value [ retrieve_decr $a ]
      lappend L [ list $name $type $value ]
      incr i
    }
    set s [ json_encode_retype_impl {*}$L ]

    store_string $result $s
  }

  proc json_encode_array { result inputs } {
    rule $inputs "json_encode_array_body $result $inputs" \
        name "json_encode_array-$result"
  }
  proc json_encode_array_body { result args } {
    set L [ list ]
    set i 0
    foreach a $args {
      set name  ""
      set value [ retrieve_decr $a ]
      set type  [ adlb::typeof  $a ]
      lappend L [ list $name $value $type ]
      incr i
    }
    set s [ json_encode_impl {*}$L ]

    store_string $result $s
  }

  proc json_encode_array_retype { result inputs } {
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_array_retype_body $result $types $inputs" \
        name "json_encode_array_retype-$result"
  }
  proc json_encode_array_retype_body { result types args } {
    set L [ list ]
    # A dict (index->"name"):
    set D_types [ adlb::enumerate $types dict all 0 1 ]

    show D_types

    json_encode_check_count $D_types $args "json_encode_array_retype" "types"

    set i 0
    foreach a $args {
      set type  [ dict get $D_types $i ]
      set value [ retrieve_decr $a ]
      lappend L [ list $name $type $value ]
      incr i
    }
    set s [ json_encode_retype_impl {*}$L ]

    store_string $result $s
  }

  proc json_encode_retype_impl { args } {
    # List of key-values
    set kvs [ list ]
    foreach ntv $args {
      # ntv: name+type+value (strings)
      # if name=="", this is encoding an array, else an object
      lassign $ntv name type value
      set token [ json_prefix $name $value ]
      switch $type {
        "float" {
          json_encode_convert json_encode_object_retype float $value
          append token [expr double($value)]
        }
        "int" {
          json_encode_convert json_encode_object_retype int $value
          append token [expr int($value)]
        }
        "string" {
          append token "\"$value\""
        }
        "boolean" {
          json_encode_convert json_encode_object_retype boolean $value
          set b [ ternary { $value == 0 } "false" "true" ]
          append token $b
        }
        "null" {
          append token "null"
        }
        "object" {
          append token "\{ $value \}"
        }
        "array" {
          append token "\[ $value \]"
        }
        "literal" {
          append token $value
        }
        default {
          turbine_error "json_encode_retype(): unknown type: $type"
        }
      }
      lappend kvs $token
    }
    set result [ join $kvs ", " ]
    return $result
  }

  proc json_encode_check_count { D a function which } {
    set D_length [ dict size $D ]
    set a_length [ llength $a ]
    if { $D_length != $a_length } {
      turbine_error \
          "${function}(): " \
          "length of $which ($D_length) not equal to " \
          "length of given values ($a_length)"
    }
  }

  proc json_encode_convert { function type value } {
    # Just a conversion check
    # Very accomodating-
    #      floats      are converted to ints
    #      floats|ints are converted to booleans
    #      strings     are parsed and converted as needed
    # Tcl has 'string is double' but Turbine calls these 'floats'
    if { [ string equal $type float   ] ||
         [ string equal $type int     ] ||
         [ string equal $type boolean ] } {
      set tcl_type double
    } else {
      set tcl_type $type
    }
    if { ! [ string is $tcl_type $value ] } {
      turbine_error \
          "${function}(): cannot convert '$value' to type '$type'"
    }
  }
}
