# Test for app retries when resources not available

package require turbine 1.0
namespace import turbine::*

proc main {  } {
    succeed
    fail 
    vwait period 
}

proc succeed { } {
	global period
    	turbine::c::log [ list exec: **** WORKING APP CALL ***** ]
	turbine::exec_external "/usr/bin/killall" [ dict create ] -9  infinite_loop.sh 
}


proc fail {  } {
	global period
	turbine::c::log [ list ********FAILING APP CALL ****** ] 
	after 5 {
		turbine::exec_external "/usr/bin/killall" [ dict create ] -9  infinite_loop.sh 
		set period some_value
		puts "period is: $period"
	}

}


turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start main
turbine::finalize


