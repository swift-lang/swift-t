
# Copyright 2018 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# LAUNCH TCL
# MPIX_Comm_launch functionality

namespace eval turbine {
  proc launch_tcl { outputs inputs args } {
    set exit_code [ lindex $outputs 0 ]
    rule $inputs "turbine::launch_tcl_body $exit_code $inputs" \
        {*}$args type $turbine::WORK
  }
  proc launch_tcl_body { exit_code args } {
    # Unpack args TDs
    lassign $args cmd argv
    # Receive MPI task information
    set comm [ turbine::c::task_comm ]
    set rank [ adlb::rank $comm ]
    # Retrieve data
    if { $rank == 0 } {
      set cmd_value [ turbine::retrieve_decr $cmd ]
    } else {
      set cmd_value {}
    }
    set length [ adlb::container_size $argv ]
    set tds    [ adlb::enumerate $argv dict all 0 ]
    # Construct a char**
    set charpp [ turbine::blob_strings_to_char_ptr_ptr $tds ]
    # Run the user code
    set exit_code_value [ launch $comm $cmd_value $length $charpp ]
    # Store result
    if { $rank == 0 } {
      store_integer $exit_code $exit_code_value
    }
  }

  proc launch_envs_tcl { outputs inputs args } {
    set exit_code [ lindex $outputs 0 ]
    rule $inputs "turbine::launch_envs_tcl_body $exit_code $inputs" \
        {*}$args type $turbine::WORK
  }
  proc launch_envs_tcl_body { exit_code args } {
    # Unpack args TDs
    lassign $args cmd argv envs
    # Receive MPI task information
    set comm [ turbine::c::task_comm ]
    set rank [ adlb::rank $comm ]
    # Retrieve data
    if { $rank == 0 } {
      set cmd_value [ turbine::retrieve_decr $cmd ]
    } else {
      set cmd_value {}
    }
    set argv_tds    [ adlb::enumerate $argv dict all 0 ]
    set argv_length [ dict size $argv_tds ]
    set envs_tds    [ adlb::enumerate $envs dict all 0 ]
    set envs_length [ dict size $envs_tds ]
    # Receive MPI task information
    set comm   [ turbine::c::task_comm ]
    set rank   [ adlb::rank $comm ]
    # Construct char**
    set argv_charpp [ turbine::blob_strings_to_char_ptr_ptr $argv_tds ]
    set envs_charpp [ turbine::blob_strings_to_char_ptr_ptr $envs_tds ]
    # Run the user code
    set exit_code_value \
		[ launch_envs $comm $cmd_value  \
			  $argv_length $argv_charpp \
			  $envs_length $envs_charpp ]
    # Store result
    if { $rank == 0 } {
      store_integer $exit_code $exit_code_value
    }
  }

  proc launch_turbine_tcl { outputs inputs args } {
    set exit_code [ lindex $outputs 0 ]
    rule $inputs "turbine::launch_turbine_tcl_body $exit_code $inputs" \
        {*}$args type $turbine::WORK
  }
  proc launch_turbine_tcl_body { exit_code args } {
    # Unpack args TDs
    lassign $args cmd argv
    # Retrieve data
    set cmd_value [ turbine::retrieve_decr $cmd ]
    set length [ adlb::container_size $argv ]
    set tds    [ adlb::enumerate $argv dict all 0 ]
    # Receive MPI task information
    set comm   [ turbine::c::task_comm ]
    set rank   [ adlb::rank $comm ]
    # Construct a char**
    set charpp [ turbine::blob_strings_to_char_ptr_ptr $tds ]
    # Run the user code
    set exit_code_value [ launch_turbine $comm $cmd_value $length $charpp ]
    # Store result
    if { $rank == 0 } {
      store_integer $exit_code $exit_code_value
    }
  }

  proc launch_multi_tcl { outputs inputs args } {
	set exit_code [ lindex $outputs 0 ]
    rule $inputs "turbine::launch_multi_tcl_body $exit_code $inputs" \
        {*}$args type $turbine::WORK
  }
  proc launch_multi_tcl_body { exit_code args } {
    # Unpack args TDs
    lassign $args procs cmd argv envs color_setting
    set procs_dict [ adlb::enumerate $procs dict all 0 ]
    set count [ dict size $procs_dict ]
    # show count
    set cmd_dict [ adlb::enumerate $cmd dict all 0 ]
    set count2 [ dict size $cmd_dict ]
    if { $count != $count2 } {
      turbine_error \
          "launch_multi(): unequal counts in arguments!"
    }

    # Extract dict data from ADLB
    set argv_dict [ adlb::enumerate $argv dict all 0 ]
    set envs_dict [ adlb::enumerate $envs dict all 0 ]
    set argc_dict [ dict create ]
    set envc_dict [ dict create ]

    # This is a nested dict [ int->int->arg ]
    set argv_all  [ dict create ]
    set argv_dict_size [ dict size $argv_dict ]
    if { $argv_dict_size != $count } {
      turbine::turbine_error "launch: Bad argument count! " \
          "Provided $argv_dict_size sets, need $count."
    }
    dict for { k v } $argv_dict {
      set a [ adlb::enumerate $v dict all 0 ]
      dict set argv_all $k $a
      set n [ dict size $a ]
      dict set argc_dict $k $n
      # show k n
    }
    # This is a nested dict [ int->int->env ]
    set envs_all  [ dict create ]
    set envs_dict_size [ dict size $envs_dict ]
    if { $envs_dict_size == 0 } {
      for { set i 0 } { $i < $count } { incr i } {
        dict set envc_dict $i 0
      }
    } elseif { $envs_dict_size != $count } {
      turbine_error "launch: Bad env var count!"
    } else {
      dict for { k v } $envs_dict {
        set a [ adlb::enumerate $v dict all 0 ]
        dict set envs_all $k $a
        set n [ dict size $a ]
        dict set envc_dict $k $n
      }
    }

    set color_setting_value [ retrieve $color_setting ]

    # show envc_dict

    # Receive MPI task information
    set comm   [ turbine::c::task_comm ]
    set rank   [ adlb::rank $comm ]

    # show rank

    # Construct int*
    set procs_int* [ turbine::blob_dict_to_int_array $procs_dict ]
    # Construct char**
    set cmd_char** [ turbine::blob_strings_to_char_ptr_ptr $cmd_dict ]
    # Construct int*
    set argc_int* [ turbine::blob_dict_to_int_array $argc_dict ]
    set envc_int* [ turbine::blob_dict_to_int_array $envc_dict ]
    # Construct char***
    set argv_char*** [ turbine::blob_string_dict_to_char_ppp $argv_all ]
    set envs_char*** [ turbine::blob_string_dict_to_char_ppp $envs_all ]

    # Run the user code
    set exit_code_value \
        [ launch_multi $comm $count ${procs_int*} \
              ${cmd_char**} \
              ${argc_int*} ${argv_char***} \
              ${envc_int*} ${envs_char***} \
              $color_setting_value ]

    # Store result
    if { $rank == 0 } {
      store_integer $exit_code $exit_code_value
    }
    # puts "returned: [ adlb::rank ]"
  }
}
