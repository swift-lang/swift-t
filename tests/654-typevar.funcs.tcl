
package provide funcs_654 2.0

namespace eval funcs_654 {
  proc mutable_ref { outputs inputs } {
    # reference is handle to data
    # We should get 1 write refcount for reference
    lassign $outputs reference referand
    
    turbine::store_integer $reference $referand
  }
  
  proc close_mutable_ref { outputs inputs } {
    set r [ lindex $inputs 0 ]
    turbine::rule $r "funcs_654::close_mutable_ref_body $r"
  }

  proc close_mutable_ref_body { r } {
    turbine::write_refcount_decr [ turbine::retrieve_decr $r ]
  }

  proc f { outputs inputs } {
    lassign $outputs r1 r2
    turbine::write_refcount_decr $r1
    turbine::write_refcount_decr $r2
  }

  proc g1 { outputs inputs } {
    turbine::write_refcount_decr [ lindex $outputs 0 ]
  }

  proc g2 { outputs inputs } {
    turbine::read_refcount_decr [ lindex $inputs 0 ]
    turbine::write_refcount_decr [ lindex $outputs 0 ]
  }
}
