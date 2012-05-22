
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
        set key_value [ retrieve_string $key ]
        store_string $result [ getenv_impl $key_value ]
    }

    proc getenv_impl { key } {
        if [ info exists env($key) ] {
            set result_value $env($key)
        } else {
            set result_value ""
        }
    }
}
