
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

    json_encode_check $D $args

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

  proc json_encode_check { D a } {
    set D_length [ dict size $D ]
    set a_length [ llength $a ]
    if { $D_length != $a_length } {
      turbine_error \
          "json_encode: length of names ($D_length) not equal to " \
          "length of given values ($a_length)"
    }
  }
}
