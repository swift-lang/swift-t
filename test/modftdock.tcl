
# ModFTDock example:
# https://sites.google.com/site/exmproject/development/task-model

package require turbine 0.1

turbine_init

global turbine_null

# Get arg("list")
set str_list [ turbine_new ]
turbine_string $str_list
turbine_string_set $str_list "list"
set td_list [ turbine_new ]
turbine_string $td_list
turbine_argv_get $td_list $str_list

# Get arg("in")
set in [ turbine_new ]
turbine_string $in
turbine_string_set $in "in"
set td_in [ turbine_new ]
turbine_string $td_in
turbine_argv_get $td_in $in

# Get arg("n")
set n [ turbine_literal string "n" ]
set td_n [ turbine_new ]
turbine_string $td_n
turbine_argv_get $td_n $n

# Convert n to integer
set td_int_n [ turbine_new ]
turbine_integer $td_int_n
turbine_toint $td_int_n $td_n

# Read roots input file
set str_roots [ turbine_new ]
turbine_container $str_roots key integer
turbine_readdata $str_roots $td_list

set slash [ turbine_literal string "/" ]
set pdb [ turbine_literal string ".pdb" ]

turbine_loop loop1_body $str_roots

proc loop1_body { key } {

    global str_roots td_in td_int_n slash pdb

    puts "body: $key"
    # turbine_trace $key
    set t [ turbine_integer_get $key ]
    puts "t: $t"
    set root [ turbine_lookup $str_roots key $t ]
    turbine_trace $root $pdb
    set s [ turbine_new ]
    turbine_string $s
    turbine_strcat $s $td_in $slash $root $pdb
    turbine_trace $s

    turbine_trace $td_int_n

    set indices [ turbine_new ]
    turbine_container $indices key integer
    # turbine_range $indices
}

# set pdb

set result [ turbine_engine ]
if { ! $result } { exit 1 }

turbine_finalize

puts OK
