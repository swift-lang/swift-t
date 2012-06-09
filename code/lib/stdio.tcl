
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

}
