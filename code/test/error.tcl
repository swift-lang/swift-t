
# Test what happens if we do not catch a Tcl error
# In a past buggy MPICH, this caused SEGVs

package require turbine 0.0.1
adlb::init 1 1

error "This is fatal"

adlb::finalize
puts OK
