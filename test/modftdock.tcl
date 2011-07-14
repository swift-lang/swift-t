
# ModFTDock example:
# https://sites.google.com/site/exmproject/development/task-model

package require turbine 0.1

turbine_init

global turbine_null

# Get arg("list")
set str_list [ turbine_new ]
turbine_string $str_list
turbine_string_set $str_list "list"
set t_list [ turbine_new ]
turbine_string $t_list
turbine_argv_get $t_list $str_list

# Get arg("in")
set in [ turbine_new ]
turbine_string $in
turbine_string_set $in "in"
set t_in [ turbine_new ]
turbine_string $t_in
turbine_argv_get $t_in $in

set str_roots [ turbine_new ]
turbine_container $str_roots key integer
turbine_readdata $str_roots $t_list

turbine_loop loop1_body $str_roots

proc loop1_body { key } {

    global str_roots

    puts "body: $key"
    # turbine_trace $key
    set t [ turbine_integer_get $key ]
    puts "t: $t"
    set value [ turbine_lookup $str_roots key $t ]
    # turbine_trace $value

    # set pdb [ turbine_new ]
    # turbine_string $pdb
    # turbine_string_set $pdb ".pdb"
}

# set pdb

set result [ turbine_engine ]
if { ! $result } { exit 1 }

turbine_finalize

puts OK
