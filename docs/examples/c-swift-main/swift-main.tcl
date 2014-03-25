
namespace eval swift_main {

    proc swift_main_wrap { rc A } {
        deeprule $A 1 0 "swift_main::swift_main_wrap_impl $rc $A"
    }

    proc swift_main_wrap_impl { rc A } {

        set length [ adlb::container_size $A ]
        set tds [ adlb::enumerate $A members all 0 ]
        set argv [ list ]
        for { set i 0 } { $i < $length } { incr i } {
            set td [ lindex $tds $i ]
            set s [ adlb::retrieve $td ]
            lappend argv $s
        }
        set rc_value [ swift_main_extension {*}$argv ]
        turbine::store_integer $rc $rc_value
    }
}
