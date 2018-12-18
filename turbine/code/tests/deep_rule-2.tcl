# Test for deep rule based on STC generated code

package require turbine 1.0
namespace import turbine::*


proc main {  } {
    turbine::c::log "enter function: main"
    lassign [ adlb::multicreate [ list integer 1 ] [ list string 1 ] [ list container integer ref 1 1 ] [ list string 1 ] [ list string 1 ] [ list string 1 ] [ list container integer file_ref 1 1 ] [ list string 1 ] [ list string 1 ] [ list string 1 ] [ list string 1 ] [ list string 1 ] ] u:done u:arg u:args t:2 t:3 t:4 u:args2 t:7 t:9 t:11 t:13 t:15
    turbine::c::log "allocated u:done=<${u:done}> u:arg=<${u:arg}> u:args=<${u:args}> t:2=<${t:2}> t:3=<${t:3}>"
    turbine::c::log "allocated t:4=<${t:4}> u:args2=<${u:args2}> t:7=<${t:7}> t:9=<${t:9}> t:11=<${t:11}>"
    turbine::c::log "allocated t:13=<${t:13}> t:15=<${t:15}>"
    # We have multiple components:
    # - A single variable argument: u:arg
    # - An array of strings u:args
    # - An array of files u:args2
    # We delay setting some of them until the rule is created
    turbine::store_string ${u:arg} "the"

    turbine::store_string ${t:2} "quick"
    turbine::store_string ${t:3} "brown"
    turbine::array_build ${u:args} [ list ${t:2} ${t:3} ${t:4} ] 1 ref

    turbine::allocate_file t:6 0 1
    turbine::allocate_file t:8 0 1
    turbine::allocate_file t:10 0 1
    turbine::allocate_file t:12 0 1
    turbine::allocate_file t:14 0 1
    turbine::store_string ${t:7} "jumped"
    turbine::store_string ${t:11} "the"
    turbine::store_string ${t:13} "lazy"
    turbine::input_file [ list ${t:8} ] [ list ${t:9} ]
    turbine::input_file [ list ${t:10} ] [ list ${t:11} ]
    turbine::input_file [ list ${t:12} ] [ list ${t:13} ]
    turbine::array_build ${u:args2} [ list ${t:6} ${t:8} ${t:10} ${t:12} ${t:14} ] 1 file_ref

    # An argument for the command name
    lassign [ adlb::multicreate [ list string 1 ] ] echo_cmd
    turbine::store_string $echo_cmd "echo"

    # Run the command once all of the above is closed
    turbine::deeprule [ list ${echo_cmd} ${u:arg} ${u:args} ${u:args2} ]\
              [ list 0 0 1 1 ] [ list ref ref ref file_ref ] \
              "echo ${u:arg} ${u:args} ${echo_cmd} ${u:done} ${u:args2}"\
              target -100 type ${::turbine::WORK}

    

    # Print a message when done
    turbine::rule [ list ${u:done} ] "report_done ${u:done}" type ${::turbine::LOCAL}

    # Test deferred dataflow
    turbine::store_string ${t:4} "fox"
    turbine::store_string ${t:9} "over"
    turbine::input_file [ list ${t:6} ] [ list ${t:7} ]
    turbine::input_file [ list ${t:14} ] [ list ${t:15} ]
    turbine::store_string ${t:15} "dog"
}

proc echo { u:arg u:args echo_cmd u:done u:args2 } {
    set v:echo_cmd [ turbine::retrieve_string ${echo_cmd} CACHED 1 ]
    set v:arg [ turbine::retrieve_string ${u:arg} CACHED 1 ]
    set tcltmp:unpacked2 [ turbine::unpack_args ${u:args} 1 ref ]
    set tcltmp:unpacked3 [ turbine::unpack_args ${u:args2} 1 file_ref ]
    turbine::c::log [ list exec: /usr/bin/env ${v:echo_cmd} ${v:arg} {*}${tcltmp:unpacked2} {*}${tcltmp:unpacked3} [ dict create ] ]
    turbine::exec_external "/usr/bin/env" [ dict create ] ${v:echo_cmd} ${v:arg} {*}${tcltmp:unpacked2} {*}${tcltmp:unpacked3}
    turbine::store_void ${u:done}
    turbine::read_refcount_decr ${u:args} 1
    turbine::read_refcount_decr ${u:args2} 1
}

proc report_done { u:done } {
    lassign [ adlb::multicreate [ list integer 0 ] [ list string 1 ] ] t:16 t:17
    # Swift l.9 evaluating  expression and throwing away 1 results
    turbine::store_string ${t:17} "DONE!"
    turbine::trace [ list ${t:16} ] [ list ${t:17} ]
    turbine::read_refcount_decr ${u:done} 1
}


turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start main
turbine::finalize


