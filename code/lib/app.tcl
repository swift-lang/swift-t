# Turbine APP.TCL

# Functions for launching external apps
namespace eval turbine {
  
  # Run external execution
  # cmd: executable to run
  # args: command line args as strings
  proc exec_external { cmd args } {
    exec $cmd {*}$args >@stdout 2>@stderr
  }
}
