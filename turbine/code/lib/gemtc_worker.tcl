# GEMTC.TCL
# Replacement worker for GEMTC tasks
# Requires GeMTC module to be loaded

namespace eval turbine {

    # If this GEMTC was initialized, this is 1
    variable gemtc_initialized
    # Dict of context dicts indexed by rule_id
    variable gemtc_context
    # Number of GEMTC tasks currently running
    variable gemtc_running
    # Maximal number concurrent GEMTC tasks to run
    variable gemtc_limit
      
    # Check to run alternative GeMTC worker.
    # Return 1 if ran GeMTC worker, 0 otherwise
    proc gemtc_alt_worker {} {
        # Alternative GEMTC worker is enabled by environment variable
        # TURBINE_GEMTC_WORKER=1, or another non-zero value
        # An empty string is treated as false, other values are invalid
        global env
        if { [ info exists env(TURBINE_GEMTC_WORKER) ] &&
             $env(TURBINE_GEMTC_WORKER) != "" } {
            set gemtc_setting $env(TURBINE_GEMTC_WORKER)
            if { ! [ string is integer -strict $gemtc_setting ] } {
              error "Invalid TURBINE_GEMTC_WORKER setting, must be int:\
                     ${gemtc_setting}"
            }

            if { $gemtc_setting } {
             gemtc_worker
             return 1
            }
        }
        return 0
    }

    # Main worker loop
    proc gemtc_worker { } {
        # Load gemtc only when worker starts
        package require gemtc

	# call srand once here
	set seed [ expr [adlb::comm_rank] + [ clock milliseconds ] ]
	expr { srand ($seed) }

	# intialize context
        variable gemtc_initialized
        variable gemtc_running

        if { ! [ info exists gemtc_initialized ] } {
            set gemtc_initialized 1
            set gemtc_context [ dict create ]
            set gemtc_running 0
            set gemtc_limit 1000000000
        }

	# This is a hack for now. We know that we want to make GEMTC calls.
	# At some point we should introduce logic to call GEMTC only if needed
	GEMTC_Setup 100000000
        global WORK_TYPE

        while { true } {
            #puts "gemtc_running: $gemtc_running"
            if { $gemtc_running < $gemtc_limit } {
                if { $gemtc_running == 0 } {
                    set msg [ adlb::get $WORK_TYPE(WORK) answer_rank ]
                } else {
                    set msg [ adlb::iget $WORK_TYPE(WORK) answer_rank ]
                }

                if { ! [ string length $msg ] } {
                    #puts "empty calling cleanup"
                    GEMTC_Cleanup
		    break
                } elseif { ! [ string equal $msg ADLB_NOTHING ] } {
                    set rule_id [ lreplace $msg 1 end ]
                    set command [ lreplace $msg 0 0 ]
                    do_work $answer_rank $rule_id $command
                }
            }
            gemtc_check
        }
    }

    # Worker: do actual work, handle errors, report back when complete
    proc do_work { answer_rank rule_id command } {

        global WORK_TYPE

        debug "rule_id: $rule_id"
        debug "work: $command"
        debug "eval: $command"

	# Get a substring of the command to
	# help determine if it starts with "gemtc_"
	set checkString [string range $command 0 5]

	# Determine if $command is normal or should call gemtc
        if { [ string compare $checkString "gemtc_" ] == 1 } {

            # Normal Turbine command
            # debug "Normal Turbine Command"
            if { [ catch { eval $command } e ] } {
                # puts "Normal Turbine Command Error"
		# puts "work unit error: "
                # puts $e
                # puts "[dict get $e -errorinfo]"
                error "rule: transform failed in command: $command"
            }
        } else {
	    # gemtc worker launch
	    gemtc_do_work $rule_id $command
        }
    }

    proc gemtc_do_work { rule_id command } {
	
	# puts "in gemtc do work"

        variable gemtc_context

        variable gemtc_running

        incr gemtc_running

	# parse out args for general information
        set subcommand_args [ string range $command 6 end ]
        set subcommand [ lreplace $subcommand_args 1 end ]
        set args [ lreplace $subcommand_args 0 0 ]

	# get app specific details
	switch $subcommand {
	    sleep {
		# get swift app parameters
		set sup [ gemtc::gemtc_sleep_begin {*}$args ]
		set context [ dict create cmd $subcommand args $args result NULL sup $sup ]
		dict set gemtc_context $rule_id $context
		
		if { [ catch { gemtc_put_sleep $rule_id $command $sup } e ] } {
		    #puts "work unit error in gemtc_put: "
		    #puts $e
		    error "rule: transform failed in command: $command"
		}
	    }
	    mdproxy {
		
		#puts "in mdproxy in gemtc do work"

		# get swift app parameters
		set sup [ gemtc::gemtc_mdproxy_begin {*}$args ]		
		set context [ dict create cmd $subcommand args $args result NULL sup $sup]
		dict set gemtc_context $rule_id $context
		
		if { [ catch { gemtc_put_mdproxy $rule_id $command $sup } e ] } {
		    #puts "work unit error in gemtc_put: "
		    #puts $e
		    error "rule: transform failed in command: $command"
		}
	    }
	}
    }

    proc gemtc_put_sleep { rule_id command swift_user_param } {

        variable gemtc_context

	# Get the context
	set context [ dict get $gemtc_context $rule_id ]

	# Get the size of an int
	set sizeOfInt [ GEMTC_SizeOfInt ]

	# Create a host sleeptime pointer
	set h_sleeptime_ptr [ GEMTC_CPUMalloc $sizeOfInt ]

	# Set the sleeptime from the swift user params
	GEMTC_CPU_SetInt $h_sleeptime_ptr $swift_user_param

	# Device sleeptime pointer
	set d_sleeptime [ GEMTC_GPUMalloc $sizeOfInt ]

	# Add pointers to a CPU list and GeMTC list to free later
	set cpu_freeme $h_sleeptime_ptr	
	set gemtc_freeme $d_sleeptime
	
	# put for debug
	# puts "cpu_freeme: $cpu_freeme"
	# puts "gemtc_freeme: $gemtc_freeme"

	# Update the context
	dict set context cpu_freeme $cpu_freeme 
	dict set context gemtc_freeme $gemtc_freeme
	
	# Now update the master dict
	dict set gemtc_context $rule_id $context

	# Copy the sleeptime into device memory
	GEMTC_MemcpyHostToDevice $d_sleeptime $h_sleeptime_ptr $sizeOfInt
	
	# Push the task to GeMTC
	GEMTC_Push 0 32 $rule_id $d_sleeptime
    }
    proc gemtc_put_mdproxy { rule_id command swift_user_param } {

	variable gemtc_context

	#	puts "in gemtc_put_mdproxy"

	# Get the context
	set context [ dict get $gemtc_context $rule_id ]
	
	 # Get Sizes
	 set sizeOfInt [ GEMTC_SizeOfInt ]
	 set sizeOfLongInt [ GEMTC_SizeOfLongInt ]
	 set sizeOfDouble [ GEMTC_SizeOfDouble ]
	 set sizeOfFloat [ blobutils_sizeof_float ]

	 set h_np_ptr [ GEMTC_CPUMalloc $sizeOfFloat ]
	 set h_nd_ptr [ GEMTC_CPUMalloc $sizeOfFloat ]
	 set h_mass_ptr [ GEMTC_CPUMalloc $sizeOfFloat ]

	 # up to here things are dynamic
	 set h_np_params [ lindex $swift_user_param 0 ]

	 GEMTC_CPU_SetLongInt $h_np_ptr $h_np_params

	 # These are hard coded within GeMTC for now, so hard coding them into the logic for now as well. -Scott
	 # get the number of dimensions from swift
	 GEMTC_CPU_SetLongInt $h_nd_ptr 3
	 GEMTC_CPU_SetDouble $h_mass_ptr 1.0

	 set h_np_value [ GEMTC_CPU_GetLongInt $h_np_ptr ]
	 set h_nd_value [ GEMTC_CPU_GetLongInt $h_nd_ptr ]
	 set h_a_size_value [ expr "$h_np_value * $h_nd_value"]
	 set h_a_mem_value [ expr "$h_a_size_value * $sizeOfDouble"]

	 # Now we need to allocate device arrays
	 set mem_needed [ expr "$sizeOfLongInt * 2 + $sizeOfDouble + $h_a_mem_value * 6" ]
	 # puts "Mem Needed: $mem_needed"

	 # Get a void pointer
	 set h_table_ptr [ GEMTC_GetVoidPointerWithSize $mem_needed ]
	 # puts "h_table_ptr is: $h_table_ptr"

	 # Set the table pointer
	 SetVoidPointerWithOffset $h_table_ptr $h_np_ptr $sizeOfLongInt 0
	 SetVoidPointerWithOffset $h_table_ptr $h_nd_ptr $sizeOfLongInt $sizeOfLongInt
	 SetVoidPointerWithOffset $h_table_ptr $h_mass_ptr $sizeOfDouble [expr "$sizeOfLongInt * 2"]

	 ###Position Array###
	 set position_blob [ blobutils_create [ lindex $swift_user_param 1 ] [ lindex $swift_user_param 2 ] ]
	 # puts "position_blob: $position_blob"

	 set h_posArray_ptr [ turbine_blob_pointer_get $position_blob ]
	 # puts "h_posArray_ptr: $h_posArray_ptr"

	 ###Other Array###
	 # get a new blob, pointer, set blob details
	 set my_other_turbine_blob [ new_turbine_blob ]
	 set h_dummyArray_ptr [ blobutils_malloc $h_a_mem_value ]
	 turbine_blob_length_set $my_other_turbine_blob $h_a_size_value
	 turbine_blob_pointer_set $my_other_turbine_blob $h_dummyArray_ptr
	 # puts "my_other_turbine_blob: $my_other_turbine_blob"
	 # puts "blob_length: [ turbine_blob_length_get $my_other_turbine_blob ]"
	 # puts "h_dummyArray_ptr: $h_dummyArray_ptr"

	 # Fill other array with zeros
	 GEMTC_ZeroDoubleArray $h_dummyArray_ptr $h_a_size_value

	 # hard coded
	 SetVoidPointerWithOffset $h_table_ptr $h_posArray_ptr $h_a_mem_value 24

	 # hard coded
	 for { set i 1 } { $i < 6 } { incr i } {
	     SetVoidPointerWithOffset $h_table_ptr $h_dummyArray_ptr $h_a_mem_value [expr "24 + $i * $h_a_mem_value"]
	 }

	 #Uncomment the line below to print all the parameters.
	 #dumpParams $h_table_ptr

	 set d_table_ptr [ GEMTC_GPUMalloc $mem_needed ]
	 # puts "d_table_ptr: $d_table_ptr"
	 ## GEMTC_MemcpyHostToDevice *host *device sizeof(int)
	 # puts "Starting memcpy to dev from tcl"
	 GEMTC_MemcpyHostToDevice $d_table_ptr $h_table_ptr $mem_needed

	 # puts "Dumping parameters before gemtc_push"
	 #dumpParams $h_table_ptr

	# create cpu free list
	set cpu_freeme [concat $h_np_ptr $h_nd_ptr $h_mass_ptr]
	# puts "created cpu_freeme"


	set gemtc_freeme $d_table_ptr
	# puts "created gemtc_freeme"

	# create gemtc free list

	# Update the context
	dict set context cpu_freeme $cpu_freeme 
	#puts "update context cpu"
	dict set context gemtc_freeme $gemtc_freeme
	#puts "update context gemtc"

	# Now update the master dict
	dict set gemtc_context $rule_id $context
	#puts "update gemtc_context"

	# puts "Starting push from tcl"
	GEMTC_Push 20 32 $rule_id $d_table_ptr
	#puts "called push"
    }

     proc gemtc_check { } {

	 # puts "in gemtc_check"

	 variable gemtc_context
	 variable gemtc_running

	 # Call gemtc_get
	 set rule_id [ gemtc_get ]
	 if { $rule_id == -1 } {
	     return
	 }

	 # GPU continuation
	 # puts "continuation..."
	 set context      [ dict get $gemtc_context $rule_id ]
	 set cpu_freeme   [ dict get $context cpu_freeme ]
	 set gemtc_freeme [ dict get $context gemtc_freeme ]
	 set cmd          [ dict get $context cmd ]
	 set args         [ dict get $context args ]
	 set value        [ dict get $context result ]
	 set sup          [ dict get $context sup ]
	 
	 switch $cmd {
	     sleep {
		 # gemtc sleep end
		 gemtc::gemtc_sleep_end {*}$args $value

		 # freeme
		 # puts "cpu_freeme end: $cpu_freeme"
		 # puts "gemtc_freeme end: $gemtc_freeme"

	     }
	     mdproxy {

		 # puts "check mdproxy"
		 
		 set hard_args [concat [lindex $args 0] [lindex $args 2]]
		 #set sup [ gemtc::gemtc_mdproxy_begin {*}$args ]
		 gemtc::gemtc_mdproxy_end {*}$hard_args $value $sup
	     }
	 }

	 #for all in cpu_freeme
	 #free(list item)
	 foreach x $cpu_freeme {
	     # puts "attempting to cpu_free $x"
	     GEMTC_CPUFreeVP $x
	     # puts "freed $x"
	 }
	 # puts "passed cpu free"
	 foreach y $gemtc_freeme {
	     # puts "attempting to gemtc_free $y"
	     GEMTC_GPUFree $y
	     # puts "free $y"
	 }
	 # puts "passed gpu free"

	 #for all in gemtc_freeme
	 #gemtc_free(list item)
	 dict unset gemtc_context $rule_id

	 incr gemtc_running -1
     }

     proc gemtc_get { } {

	 # puts "gemtc_get"

	 variable gemtc_context

	 # get size of int
	 set sizeOfInt [ GEMTC_SizeOfInt ]

	 # These need to be added to the free list! TODO
	 # Allocate some pointers
	 set h_ID_ptr [ GEMTC_CPUMallocInt $sizeOfInt ]
	 set d_params_ptr_ptr [ GEMTC_CPUMallocVPP $sizeOfInt ]
	 set h_params_ptr [ GEMTC_CPUMallocVP $sizeOfInt ]
	 
	 	 
	 # Call Poll
	 GEMTC_Poll $h_ID_ptr $d_params_ptr_ptr
	 set h_ID_value [ GEMTC_CPU_GetInt $h_ID_ptr ]
	 if { $h_ID_value == -1 } {
	     # nothing completed
	     # delay for debugging
		 after 100
		     # debug "calling poll"
			 return -1
	 }

	 # something completed - retrieve result
	 set rule_id $h_ID_value

	 # Update vars from dict
	 set context [ dict get $gemtc_context $rule_id ]
	 set cmd    [ dict get $context cmd ]
	 set args   [ dict get $context args ]
	 set value  [ dict get $context result ]
	 set cpu_freeme   [ dict get $context cpu_freeme ]
	 set gemtc_freeme [ dict get $context gemtc_freeme ]

	 # concat the general use pointers to the list
	 set cpu_freeme [concat $cpu_freeme $h_ID_ptr $d_params_ptr_ptr $h_params_ptr]
	 # puts "cpu_freeme get general: $cpu_freeme"


	 switch $cmd {
	     sleep {
		 set d_params_ptr [ GEMTC_CPUMallocVP $sizeOfInt ]
		 set d_params_ptr [VPFromVPP $d_params_ptr_ptr]
		 ## GEMTC_MemcpyHostToDevice *host *device sizeof(int)
		 GEMTC_MemcpyDeviceToHost $h_params_ptr $d_params_ptr $sizeOfInt
		 set resultInt [ IntFromVP $h_params_ptr ]

		 # Update the context with pointer lists
		 dict set context cpu_freeme $cpu_freeme 
		 dict set context gemtc_freeme $gemtc_freeme
	
		 ## Maybe the following two can be done at once?
		 # Now update the master dict
		 dict set gemtc_context $rule_id $context
		 # store result and pointers to free in context
		 dict set gemtc_context $rule_id result $resultInt
	     }
	     mdproxy {
		 
		 set sup [ gemtc::gemtc_mdproxy_begin {*}$args ]

		 set np [ lindex $sup 0 ]
		 # puts "np: $np"
		 set mem_needed [expr 8 * $np * 2 * 6 + 24]
		 
		 # malloc a huge pointer
		 set h_result_table_ptr [ GEMTC_GetVoidPointerWithSize $mem_needed ]
		 set d_table_ptr [VPFromVPP $d_params_ptr_ptr]

		 #puts "starting copy back"
		 # copy back into it
		 GEMTC_MemcpyDeviceToHost $h_result_table_ptr $d_table_ptr $mem_needed

		 #puts "dumping params"
		 # dump params to verify
		 #dumpParams $h_result_table_ptr

		 # here nd is hard coded to 2
		 set resultArraySize [ expr $np * 8 * 2 ]
		 #puts "resultArraySize: $resultArraySize"
		 
		 # 8 * np * nd
		 #set h_test_this_ptr [ GEMTC_GetVoidPointerWithSize 160 ]
		 set h_test_this_ptr [ GEMTC_GetVoidPointerWithSize $resultArraySize ]
		 
		 # Here you parse out the result starting after an offset of 24
		 
		 # 8 * np * nd
		 #GetHardCodedResult $h_test_this_ptr $h_result_table_ptr 160 24
		 GetHardCodedResult $h_test_this_ptr $h_result_table_ptr $resultArraySize 24
		 
		 set numElements [expr $np * 2]
		 
		 # Update the context with pointer lists
		 dict set context cpu_freeme $cpu_freeme 
		 dict set context gemtc_freeme $gemtc_freeme

		 # dict set gemtc_context $rule_id result $h_result_table_ptr
		 dict set gemtc_context $rule_id result $h_test_this_ptr
	     }
	 }
	 return $rule_id
     }
    
    # This function takes a string command to parse
    # then gets the string representation of the TaskID
    proc gemtc_get_taskid { stringToParse } {
	#puts "in gemtc_get_taskid"
	# Parse gemtc_ command to get details
	set cmdLen [ string length $stringToParse ]
	# debug "String length is: $cmdLen "

	# Check Switch to see which code to execute
	# Using 6 here due to the 6 chars in "gemtc_"
	set rest [ string range $stringToParse 6 $cmdLen ]
	#puts "The rest of the string is: $rest"
	set dashIndex [ string first "-" $rest ]
        #puts "Dash index in rest is: $dashIndex"

	set TaskID [ string range $rest 0 [ expr $dashIndex - 1 ] ]
	#puts "TaskID in parse: $TaskID"
	return $TaskID
    }

    proc gemtc_get_taskid_int { stringToCheck } {
	## CODE TO-DO - add more ifs or change to swift
	# Switch statment assigning ints from string value checks
	if { [ string compare $stringToCheck "sleep" ] == 0} {
	    return 0
	}
	if { [ string compare $stringToCheck "mdproxy" ] == 0} {
	    return 20
	}
    }
}
