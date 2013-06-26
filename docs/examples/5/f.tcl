
package provide my_pkg 0.0

namespace eval my_pkg {
    proc f { outputs inputs } {
        set x [ lindex $inputs  0 ]
        set y [ lindex $inputs  1 ]
        set z [ lindex $outputs 0 ]
        rule [ list $x $y ] "my_pkg::f_body $z $x $y"
    }
    proc f_body { z x y } {
        set x_value [ retrieve_integer $x ]
        set y_value [ retrieve_integer $y ]
        set z_value [ expr $x_value + $y_value ]
        store_integer $z $z_value
    }
}
