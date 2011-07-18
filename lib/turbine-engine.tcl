
# Turbine TCL library

package provide turbine 0.1

namespace eval turbine {

    proc engine { } {

        namespace import c::debug c::push c::ready c::complete c::executor

        debug "engine start..."

        push

        while {true} {

            set ready [ ready ]
            if { ! [ string length $ready ] } break

            foreach transform $ready {
                set command [ executor $transform ]
                debug "executing: $command"
                if { [ catch { turbine::eval $command } e v ] } {
                    puts "[ dict get $v -errorinfo]"
                    puts "\nrule $transform failed: $command\n"
                    return false
                }
                complete $transform
            }
        }
        debug "engine stop"
        return true
    }
}
