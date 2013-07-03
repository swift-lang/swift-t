
namespace eval g {

    proc g_tcl { outputs inputs args } {
        set k [ lindex $inputs 0 ]
        rule $k "g::g_tcl_body $k" {*}$args type $turbine::WORK
    }

    proc g_tcl_body { k } {
        set k_value [ retrieve_integer $k ]
        set comm [ turbine::c::task_comm ]
        g $comm $k
    }
}
