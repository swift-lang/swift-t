# Test for app retries when resources not available 

package require turbine 1.0
namespace import turbine::*

proc main {  } {
	puts "\n Creating a new test_file.txt"
	turbine::exec_external "/usr/bin/touch" [ dict create ] test_file.txt

	puts "\n Removing the test_file.txt - first attempt"
	turbine::exec_external "/bin/rm" [ dict create ] test_file.txt
	puts "\n Removing the test_file.txt - second attempt"
	turbine::exec_external "/bin/rm" [ dict create ] test_file.txt

}


turbine::defaults
turbine::init $servers
turbine::enable_read_refcount
turbine::start main
turbine::finalize


