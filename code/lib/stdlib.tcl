
# STDLIB.TCL
# Basic library routines

namespace eval turbine {

    namespace export getenv

    proc getenv { stack outputs inputs } {

        rule getenv-$inputs $inputs $turbine::LOCAL \
            "turbine::getenv_body $outputs $inputs"
    }
    proc getenv_body { result key } {
        global env
        set key_value [ get_string $key ]
        if [ info exists env($key_value) ] {
            set result_value $env($key_value)
        } else {
            set result_value ""
        }
        set_string $result $result_value
    }
}
