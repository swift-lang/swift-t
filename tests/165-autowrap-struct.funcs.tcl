
package provide funcs_165 0.5

namespace eval funcs_165 {
  package require turbine 0.3.0
  namespace import ::turbine::*

  proc fmt_person { person } {
    return [ fmt_person2 [ dict get $person name ] [ dict get $person age ] ]
  }

  proc fmt_person2 { name age } {
    return [ format "Name: %s Age: %d" $name $age ]
  }

  proc fmt_person_async { out inputs } {
    set person_dict [ lindex $inputs 0 ]
    set name [ dict get $person_dict name ]
    set age [ dict get $person_dict age ]
    rule "$name $age" "funcs_165::fmt_person_async_body $out $name $age" 
  }
  proc fmt_person_async_body { out name age } {
    store_string $out [ fmt_person2 [ retrieve_decr_string $name ] \
                                    [ retrieve_decr_integer $age ] ]
  }
}
