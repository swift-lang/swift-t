# Test for deep rule based on STC generated code

package require turbine 1.0
namespace import turbine::*


proc main {  } {
    turbine::c::log "enter function: main"
    set stack 0
    lassign [ adlb::multicreate [ list container integer ref 1 1 ] [ list string 1 ] [ list string 1 ] [ list string 1 ] ] words w1 w2 w3
    turbine::store_string ${w1} "quick"
    turbine::store_string ${w2} "brown"
    turbine::store_string ${w3} "fox"
    turbine::array_build ${words} [ list ${w1} ${w2} ${w3} ] 1 ref
    turbine::deeprule [ list ${words} ] [ list 1 ] [ list ref ] "echo ${stack} ${words}" target -100 type ${::turbine::WORK}
}

proc echo { stack args } {
    turbine::c::log [ list exec: /bin/echo {*}[ turbine::unpack_args ${args} 1 ref ] [ dict create ] ]
    turbine::exec_external "/bin/echo" [ dict create ] "echo:" {*}[ turbine::unpack_args ${args} 1 ref ]
    turbine::read_refcount_decr ${args} 1
}

turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start main
turbine::finalize


