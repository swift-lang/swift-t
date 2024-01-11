
# FIND TCLCONFIG TCL
# Find the directory containing tclConfig.sh
# From: https://wiki.tcl-lang.org/page/Finding+out+tclConfig.sh

puts [ ::tcl::pkgconfig get libdir,install ]
