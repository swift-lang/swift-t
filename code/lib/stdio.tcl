
# I/O library routines

namespace eval turbine {

    # namespace export printf

    proc printf { args } {

        set a [ lindex $args 2 ]
        rule printf $a $turbine::LOCAL \
            "printf_body $a"
    }
    proc printf_body { args } {
        set L [ list ]
        foreach a $args {
            lappend L [ retrieve $a ]
        }
        set s [ eval format $L ]
        puts $s
    }

    proc sprintf { stack result inputs } {
        rule sprintf $inputs $turbine::LOCAL \
            "sprintf_body $result $inputs"
    }
    proc sprintf_body { result args } {
        set L [ list ]
        foreach a $args {
            lappend L [ retrieve $a ]
        }
        set s [ eval format $L ]
        store_string $result $s
    }
}
