
# JSON TCL
# Special features for handling JSON

# Encode paths:
#  Simple encodes: use json_encode_infer to infer
#                  from Turbine types (via adlb::typeof)
#  retype/format encode: use json_encode_translate to translate
#                        from user type strings

# Major sections are prefixed with ##

namespace eval turbine {

  ## ARRAY
  proc json_encode_array { result inputs } {
    rule $inputs "json_encode_array_body $result $inputs" \
        name "json_encode_array-$result"
  }
  proc json_encode_array_body { result args } {
    set s [ json_encode_array_contents_action $result {*}$args ]
    store_string $result "\[$s\]"
  }
  proc json_encode_array_contents { result inputs } {
    rule $inputs "json_encode_array_contents_body $result $inputs" \
        name "json_encode_array-$result"
  }
  proc json_encode_array_contents_body { result args } {
    set s [ json_encode_array_contents_action $result {*}$args ]
    store_string $result $s
  }
  proc json_encode_array_contents_action { result args } {
    set L [ list ]
    set i 0
    foreach a $args {
      set name  ""
      set type  [ adlb::typeof  $a ]
      set value [ retrieve_decr $a ]
      lappend L [ list $name $value $type ]
      incr i
    }
    set s [ json_encode_infer {*}$L ]
    return $s
  }

  ## ARRAY RETYPE
  proc json_encode_array_retype { result inputs } {
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_array_retype_body $result $types $inputs" \
        name "json_encode_array_retype-$result"
  }
  proc json_encode_array_retype_body { result types args } {
    set s [ json_encode_array_contents_retype_action $types {*}$args ]
    store_string $result "\[$s\]"
  }
  proc json_encode_array_contents_retype { result inputs } {
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_array_contents_retype_body $result $types $inputs" \
        name "json_encode_array_retype-$result"
  }
  proc json_encode_array_contents_retype_body { result types args } {
    set s [ json_encode_array_contents_retype_action $types {*}$args ]
    store_string $result $s
  }
  proc json_encode_array_contents_retype_action { types args } {
    # A dict (index->"name"):
    set D_types [ adlb::enumerate $types dict all 0 1 ]

    json_encode_check_count [ dict size $D_types ] $args \
        "json_encode_array_retype" "types"

    set L [ list ]
    set i 0
    foreach a $args {
      set name  ""
      set type  [ dict get $D_types $i ]
      set value [ retrieve_decr $a ]
      lappend L [ list $name $type $value ]
      incr i
    }
    set s [ json_encode_translate {*}$L ]
    return $s
  }

  ## ARRAY FORMAT
  proc json_encode_array_format { result inputs } {
    set fmt [ list_pop_first inputs ]
    rule $inputs "json_encode_array_format_body $result $fmt $inputs" \
        name "json_encode_array_format-$result"
  }
  proc json_encode_array_format_body { result fmt args } {
    set s [ json_encode_array_contents_format_action $fmt {*}$args ]
    store_string $result "\[$s\]"
  }
  proc json_encode_array_contents_format { result inputs } {
    set fmt [ list_pop_first inputs ]
    rule $inputs "json_encode_array_contents_format_body $result $fmt $inputs" \
        name "json_encode_array_format-$result"
  }
  proc json_encode_array_contents_format_body { result fmt args } {
    set s [ json_encode_array_contents_format_action $fmt {*}$args ]
    store_string $result $s
  }
  proc json_encode_array_contents_format_action { fmt args } {

    set fmt_value [ retrieve $fmt ]
    set fmts [ ::split $fmt_value ]
    # show fmts

    json_encode_check_count [ llength $fmts ] $args \
        "json_encode_array_format" "types"

    set L [ list ]
    set i 0
    foreach a $args {
      set name  ""
      set fmti  [ lindex $fmts $i ]
      if { [ string first "%" $fmti ] != 0 } {
        turbine_error "json_encode_array_format(): " \
                      "format token without %: '$fmti'"
      }
      set value [ retrieve_decr $a ]
      lappend L [ list $name $fmti $value ]
      incr i
    }
    set s [ json_encode_translate {*}$L ]
    return $s
  }

  ## OBJECT
  proc json_encode_object { result inputs } {
    set names [ list_pop_first inputs ]
    rule $inputs "json_encode_object_body $result $names $inputs" \
        name "json_encode_object-$result"
  }
  proc json_encode_object_body { result names args } {
    set s [ json_encode_object_contents_action $result $names {*}$args ]
    store_string $result "\{$s\}"
  }
  proc json_encode_object_contents { result inputs } {
    set names [ list_pop_first inputs ]
    rule $inputs "json_encode_object_contents_body $result $names $inputs" \
        name "json_encode_object-$result"
  }
  proc json_encode_object_contents_body { result names args } {
    set s [ json_encode_object_contents_action $result $names {*}$args ]
    store_string $result $s
  }
  proc json_encode_object_contents_action { result names args } {
    # A dict (index->"name"):
    set D [ adlb::enumerate $names dict all 0 1 ]

    json_encode_check_count [ dict size $D ] $args \
        "json_encode_object" "names"

    set L [ list ]
    set i 0
    foreach a $args {
      set name  [ dict get $D $i ]
      set type  [ adlb::typeof  $a ]
      set value [ retrieve_decr $a ]

      lappend L [ list $name $value $type ]
      incr i
    }
    set s [ json_encode_infer {*}$L ]
    return $s
  }

  ## OBJECT RETYPE
  proc json_encode_object_retype { result inputs } {
    set names [ list_pop_first inputs ]
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_object_retype_body $result $names $types $inputs" \
        name "json_encode_object_retype-$result"
  }
  proc json_encode_object_retype_body { result names types args } {
    set s [ json_encode_object_contents_retype_action $names $types {*}$args ]
    store_string $result "\{$s\}"
  }
  proc json_encode_object_contents_retype { result inputs } {
    set names [ list_pop_first inputs ]
    set types [ list_pop_first inputs ]
    rule $inputs "json_encode_object_contents_retype_body $result $names $types $inputs" \
        name "json_encode_object_retype-$result"
  }
  proc json_encode_object_contents_retype_body { result names types args } {
    set s [ json_encode_object_contents_retype_action $names $types {*}$args ]
    store_string $result $s
  }
  proc json_encode_object_contents_retype_action { names types args } {
    # A dict (index->"name"):
    set D_names [ adlb::enumerate $names dict all 0 1 ]
    set D_types [ adlb::enumerate $types dict all 0 1 ]

    # show D_names D_types

    json_encode_check_count [ dict size $D_names ] $args \
        "json_encode_object_retype" "names"
    json_encode_check_count [ dict size $D_types ] $args \
        "json_encode_object_retype" "types"

    set L [ list ]
    set i 0
    foreach a $args {
      set name  [ dict get $D_names $i ]
      set type  [ dict get $D_types $i ]
      set value [ retrieve_decr $a ]
      lappend L [ list $name $type $value ]
      incr i
    }
    set s [ json_encode_translate {*}$L ]
    return $s
  }

  ## OBJECT FORMAT
  proc json_encode_object_format { result inputs } {
    set names [ list_pop_first inputs ]
    set fmt   [ list_pop_first inputs ]
    rule $inputs "json_encode_object_format_body $result $names $fmt $inputs" \
        name "json_encode_object_format-$result"
  }
  proc json_encode_object_format_body { result names fmt args } {
    set s [ json_encode_object_contents_format_action $names $fmt {*}$args ]
    store_string $result "\{$s\}"
  }
  proc json_encode_object_contents_format { result inputs } {
    set names [ list_pop_first inputs ]
    set fmt   [ list_pop_first inputs ]
    rule $inputs "json_encode_object_contents_format_body $result $names $fmt $inputs" \
        name "json_encode_object_format-$result"
  }
  proc json_encode_object_contents_format_body { result names fmt args } {
    set s [ json_encode_object_contents_format_action $names $fmt {*}$args ]
    store_string $result $s
  }
  proc json_encode_object_contents_format_action { names fmt args } {
    # A dict (index->"name"):
    set D_names [ adlb::enumerate $names dict all 0 1 ]

    set fmt_value [ retrieve $fmt ]
    set fmts [ ::split $fmt_value ]
    # show fmts

    json_encode_check_count [ dict size $D_names ] $args \
        "json_encode_object_format" "names"
    json_encode_check_count [ llength $fmts ] $args \
        "json_encode_object_format" "specifiers"

    set L [ list ]
    set i 0
    foreach a $args {
      set name  [ dict get $D_names $i ]
      set fmti  [ lindex $fmts $i ]
      if { [ string first "%" $fmti ] != 0 } {
        turbine_error "json_encode_object_format(): " \
            "format token without %: '$fmti'"
      }
      set value [ retrieve_decr $a ]
      lappend L [ list $name $fmti $value ]
      incr i
    }
    set s [ json_encode_translate {*}$L ]
    return $s
  }

  ## UTILITIES

  proc json_encode_infer { args } {
    # Encode and return all args in comma separated list,
    #        possibly with "name": prefixes
    # Uses Turbine type inferencing to retain Turbine types

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
    # Sets up the prefix token for an array or object.
    # The value will be appended later.
    if { $name eq "" } {
      # JSON array - no prefix
      set token ""
    } else {
      # JSON object - prefix is "name":
      set token "\"$name\":"
    }
    return $token
  }

  proc json_encode_translate { args } {
    # Encode and return all args as requested type
    #        in comma separated list,
    #        possibly with "name": prefixes
    # args: list of ntv: name,type,value (strings)
    # List of results
    set L [ list ]
    foreach ntv $args {
      set token [ json_encode_value {*}$ntv ]
      lappend L $token
    }
    set result [ join $L ", " ]
    return $result
  }

  proc json_encode_value { name type value } {
    # Encodes and returns the value as the given type
    # The type can be a name or a %-specifier
    set token [ json_prefix $name $value ]
    if { [ string index $type 0 ] eq "%" } {
      # A %-specifier - we switch on the last character
      set spec [ string index $type end ]
    } else {
      # A type name - we switch on the whole name
      set spec $type
    }
    # show type spec value
    switch -regexp $spec {
      "^f$" {
        json_conversion_check json_encode_format float $value
        set value [ format $type $value ]
        append token $value
      }
      "^i$" {
        json_conversion_check json_encode_format int $value
        set value [ format $type [ expr int($value) ] ]
        append token $value
      }
      "^s$" {
        set value [ format $type $value ]
        append token "\"$value\""
      }
      "int" {
        json_conversion_check json_encode_retype int $value
        append token [expr int($value)]
      }
      "float" {
        json_conversion_check json_encode_retype float $value
        append token [expr double($value)]
      }
      "string" {
        append token "\"$value\""
      }
      "array|^A$" {
        append token "\[ $value \]"
      }
      "boolean|^B$" {
        json_conversion_check json_encode_retype boolean $value
        set b [ ternary { $value == 0 } "false" "true" ]
        append token $b
      }
      "json|^J$" {
        append token $value
      }
      "null|^N$" {
        append token "null"
      }
      "object|^O$" {
        append token "\{ $value \}"
      }
      default {
        turbine_error "json_encode_format(): unknown format: '$fmt'"
      }
    }
    return $token
  }

  proc json_encode_check_count { D_length a function which } {
    # Just check that the counts are equal
    # D_length: number
    # a: list
    # function, which: just for error messages
    set a_length [ llength $a ]
    if { $D_length != $a_length } {
      turbine_error \
          "${function}():" \
          "length of $which ($D_length) not equal to " \
          "length of given values ($a_length)"
    }
  }

  proc json_conversion_check { function type value } {
    # Just a conversion check that value can be converted to type
    # function: Just for error messages
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
