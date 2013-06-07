
namespace eval f {

    proc f_tcl { k } {
        rule $k "f_tcl_body $k"
    }

    proc f_tcl_body { k } {
        set k_value [ retrieve_integer $k ]
        set comm [ turbine::task_comm ]
        f [ $comm $k ]
    }
}
