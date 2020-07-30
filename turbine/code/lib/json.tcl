
# JSON TCL
# Special features for handling JSON

namespace eval turbine {

  proc json_encode { result inputs } {
    set names [ list_pop_first inputs ]
    rule $inputs "json_encode_body $result $names $inputs" \
        name "json_encode-$result"
  }
  proc json_encode_body { result names args } {
    set L [ list ]
    # A dict (index->"name"):
    set D [ adlb::enumerate $names dict all 0 1 ]

    json_encode_check $D $args "json_encode" "names"

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
  proc json_encode_impl { args } {
    # List of key-values
    set kvs [ list ]
    # nvt: name+value+type (strings)
    foreach nvt $args {
      lassign $nvt name value type
      if { $type eq "float" || $type eq "integer" } {
        lappend kvs "\"$name\": $value"
      } elseif { $type eq "string" } {
        lappend kvs "\"$name\": \"$value\""
      }
    }
    set result [ join $kvs ", " ]
    return $result
  }

  proc json_encode_retype { result inputs } {
    set names [ list_pop_first inputs ]
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_retype_body $result $names $types $inputs" \
        name "json_encode_retype-$result"
  }
  proc json_encode_retype_body { result names types args } {
    set L [ list ]
    # A dict (index->"name"):
    set D_names [ adlb::enumerate $names dict all 0 1 ]
    set D_types [ adlb::enumerate $types dict all 0 1 ]

    show D_names D_types

    json_encode_check $D_names $args "json_encode_retype" "names"
    json_encode_check $D_types $args "json_encode_retype" "types"

    set i 0
    foreach a $args {
      set name  [ dict get $D_names $i ]
      set type  [ dict get $D_types $i ]
      set value [ retrieve_decr $a ]
      # set type  [ adlb::typeof  $a ]
      lappend L [ list $name $type $value ]
      incr i
    }
    set s [ json_encode_retype_impl {*}$L ]

    store_string $result $s
  }
  proc json_encode_retype_impl { args } {
    # List of key-values
    set kvs [ list ]
    # ntv: name+type+value (strings)
    foreach ntv $args {
      show ntv
      lassign $ntv name type value
      switch $type {
        "float" {
          json_encode_convert json_encode_retype float $value
          lappend kvs "\"$name\":  [expr double($value)]"
        }
        "int" {
          json_encode_convert json_encode_retype int $value
          lappend kvs "\"$name\": [expr int($value)]"
        }
        "string" {
          lappend kvs "\"$name\": \"$value\""
        }
        "boolean" {
          json_encode_convert json_encode_retype boolean $value
          set b [ ternary { $value == 0 } "false" "true" ]
          lappend kvs "\"$name\": $b"
        }
        "null" {
          lappend kvs "\"$name\": null"
        }
        "object" {
          lappend kvs "\"$name\": \{ $value \}"
        }
        "array" {
          lappend kvs "\"$name\": \[ $value \]"
        }
        "literal" {
          lappend kvs "\"$name\": $value"
        }
        default {
          turbine_error "json_encode_retype(): unknown type: $type"
        }
      }
    }
    set result [ join $kvs ", " ]
    return $result
  }

  proc json_encode_check { D a function which } {
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
