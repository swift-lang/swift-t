
package provide funcs_165 0.5

namespace eval funcs_165 {
  package require turbine 1.0
  namespace import ::turbine::*

  proc fmt_person { person } {
    return [ fmt_person2 [ dict get $person name ] [ dict get $person age ] ]
  }

  proc fmt_person2 { name age } {
    return [ format "Name: %s Age: %d" $name $age ]
  }

  proc fmt_person_async { out inputs } {
    set person [ lindex $inputs 0 ]
    rule "$person" "funcs_165::fmt_person_async_body $out $person" 
  }
  proc fmt_person_async_body { out person } {
    store_string $out [ fmt_person [ retrieve_decr_struct $person ] ]
  }
}
