
# Test what happens if we try to get something that does not exist

package require turbine 0.0.1
adlb::init 1 1

if [ adlb::amserver ] {
    adlb::server
} else {

    # Intentionally let Tcl error escape
    adlb::retrieve 1

    # Use this block to catch the error:
    # if { [ catch { adlb::retrieve 1 } ] } {
    #     puts "caught error!"
    # }
}

puts DONE
adlb::finalize
puts OK
