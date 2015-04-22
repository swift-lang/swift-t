#!/bin/bash

# Clean up test suite outputs

# Note: *funcs.tcl are required for Swift/T builtin function tests

TCL=$( ls *.tcl | grep -v "funcs.tcl\|make-package.tcl\|pkgIndex.tcl" )
rm -f ${TCL}
rm -f *.ic
rm -f *.out
rm -f *.stc.*

exit 0
