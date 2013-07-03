
namespace eval f {

    proc f_tcl { outputs inputs args } {
        set k [ lindex $inputs 0 ]
        rule $k "f::f_tcl_body $k" {*}$args type $turbine::WORK
    }

    proc f_tcl_body { k } {
        set k_value [ retrieve_integer $k ]
        set comm [ turbine::c::task_comm ]
        f $comm $k
    }
}
