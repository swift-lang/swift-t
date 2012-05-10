
# Test distributed range creation functionality

# SwiftScript
# int i = 1;
# int j = 4;
# int c[] = [i:j];
# string s = @sprintf(c)
# trace(s);

package require turbine 0.0.1

proc rules { } {

    turbine::allocate i integer
    turbine::allocate j integer
    turbine::allocate_container c integer
    turbine::allocate p integer

    global env
    if { [ info exists env(COUNT) ] } {
        set count $env(COUNT)
    } else {
        set count 100
    }
    puts "count: $count"
    turbine::store_integer $i 1
    turbine::store_integer $j $count
    turbine::store_integer $p $env(TURBINE_ENGINES)

    turbine::drange $c $i $j $p
}

turbine::defaults
turbine::init $engines $servers
turbine::start rules
turbine::finalize

puts OK

# Help TCL free memory
proc exit args {}
