
# MPE TURBINE.TCL

# Misc. definitions for Turbine/MPE processing

# Include other MPE processing features...
set turbine_script      [ info script ]
set turbine_script_dir  [ file dirname $turbine_script ]
set turbine_script_file [ file tail $turbine_script ]
source $script_dir/mpe.tcl

set SERVER 0
set ENGINE 1
set WORKER 2
