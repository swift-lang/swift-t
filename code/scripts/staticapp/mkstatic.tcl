# Tcl script to generate entry point C code for statically linked 
# application.  Uses template C code and manifest file to output
# an appropriate C main program.

set SCRIPT_DIR [ file dirname [ info script ] ]
set F2A [ file join $SCRIPT_DIR "file2array.sh" ]


set INIT_PKGS_FN "InitAllPackages"
set MAIN_SCRIPT_STRING "__turbine_tcl_main"

proc main { } {
  global SCRIPT_DIR
  # TODO: compile/link options?
  set usage "mkstatic.tcl <manifest file> \[-c <output c file> \] \
        \[-p <pkgIndex.tcl file> \] \
        \[--deps <dependency include for C output> <c output file for deps> \]\
        \[--link-deps <dependency include for linking> <link target> \]\
        \[--link-objs: print list of link objects to stdout \]\
        \[--link-flags: print list of linker library flags to stdout \]\
        \[-r <non-default resource var name> \] \
        --ignore-no-manifest: if no manifest present, assume empty manifest \
        -v: verbose messages to report on process \
        -h: help \n\
        Notes: \n\
        -> --link-objs are printed before --link-flags if both provided"

  set non_flag_args [ list ]

  set c_output_file ""
  set deps_output_file ""
  set deps_c_output_file ""
  set link_deps_output_file ""
  set link_deps_target ""
  set print_link_objs 0
  set print_link_flags 0
  set pkg_index ""
  set resource_var_prefix "turbine_app_resources"
  global verbose_setting
  set verbose_setting 0
  set ignore_no_manifest 0

  for { set argi 0 } { $argi < $::argc } { incr argi } {
    set arg [ lindex $::argv $argi ]
    if { [ string index $arg 0 ] == "-" } {
      switch $arg {
        -v {
          set verbose_setting 1
        }
        -c {
          incr argi
          set c_output_file [ lindex $::argv $argi ]
          nonempty $c_output_file "Expected non-empty argument to -c"
        }
        -p {
          incr argi
          set pkg_index [ lindex $::argv $argi ]
          nonempty $pkg_index "Expected non-empty argument to -p"
        }
        -r {
          incr argi
          set resource_var_prefix [ lindex $::argv $argi ]
          nonempty $resource_var_prefix "Expected non-empty argument to -r"
        }
        --deps {
          incr argi
          set deps_output_file [ lindex $::argv $argi ]
          nonempty $deps_output_file "Expected non-empty argument to --deps"
          incr argi
          set deps_c_output_file [ lindex $::argv $argi ]
          nonempty $deps_c_output_file \
                "Expected second non-empty argument to --deps"
        }
        --link-deps {
          incr argi
          set link_deps_output_file [ lindex $::argv $argi ]
          nonempty $link_deps_output_file "Expected non-empty argument to --link-deps"
          incr argi
          set link_deps_target [ lindex $::argv $argi ]
          nonempty $link_deps_target "Expected second non-empty argument to --link-deps"
        }
        --link-objs {
          set print_link_objs 1
        }
        --link-flags {
          set print_link_flags 1
        }
        --ignore-no-manifest {
          set ignore_no_manifest 1
        }
        -h {
          puts $usage
          exit 0
        }
        default {
          user_err "Unknown flag $arg"
        }
      }
    } else {
      lappend non_flag_args $arg
    }
  }
  
  if { [ llength $non_flag_args ] != 1 } {
    user_err "Expected exactly one non-flagged argument for manifest file\
              but got: $non_flag_args"
  }
  set manifest_filename [ lindex $non_flag_args 0 ]

  set manifest_dict [ read_manifest $manifest_filename $ignore_no_manifest ]
 
  # generate deps file if needed
  if { [ string length $deps_output_file ] > 0 } {
    write_deps_file $manifest_dict $deps_output_file $deps_c_output_file
  }

  print_link_info stdout $manifest_dict $print_link_objs $print_link_flags
  
  if { [ string length $link_deps_output_file ] > 0 } {
    write_link_deps_file $manifest_dict $link_deps_output_file $link_deps_target
  }

  if { [ string length $c_output_file ] > 0 } {
    fill_c_template $manifest_dict $resource_var_prefix $c_output_file
  }
  
  if { [ string length $pkg_index ] > 0 } {
    gen_pkg_index $pkg_index [ dict get $manifest_dict pkg_name ] \
                             [ dict get $manifest_dict pkg_version ]
  }
}

proc user_err { msg } {
  puts stderr "error: $msg"
  exit 1
}

proc setonce { varname val } {
  upvar 1 $varname var 
  if { [ string length $var ] > 0 } {
    user_err "$varname had two values: $val and $var"
  }
  set var $val
}

# Read manifest file and fill in dictionary.
# All file paths in manifest are relative to the directory containing
# the manifest file: filenames in returned dictionary for files that
# are to be processed by this script are updated to account.
proc read_manifest { manifest_filename ignore_no_manifest } {
  # Initial values of things that may be specified by manifest file
  set manifest_dir [ file dirname $manifest_filename ]

  # Tcl package info
  set pkg_name ""
  set pkg_version ""

  set main_script ""

  # lib scripts are executed before main script
  set lib_scripts [ list ]

  # C function names to initialise Tcl modules
  set lib_init_fns [ list ]

  # headers for user Tcl code
  set lib_includes [ list ]

  # library objects to link against statically (e.g. .o or .a files)
  set lib_objects [ list ]

  # lib shared object (linked against - not preferred).
  # This goes last on link command
  set linker_libs ""

  set have_manifest [ file exists $manifest_filename ]

  if { ! $have_manifest && ! $ignore_no_manifest } {
    user_err "Manifest file \"$manifest_filename\" not present"
  }

  if { $have_manifest } {
    set manifest [open $manifest_filename]
    while {[gets $manifest line] >= 0} {
      set line [ string trimleft $line ]
      if { [ string length $line ] == 0 ||
           [ string index $line 0 ] == "#" } {
        # comment or empty line
        continue
      }
      set eq_index [ string first "=" $line ]
      if { $eq_index < 0 } {
        user_err "Expected = sign in manifest file line"
      }
      # Remove whitespace from key
      set key [ string trim [ string range $line 0 [ expr $eq_index - 1 ] ] ]
      # Keep whitespace for value in case it's significant - can be stripped later
      set val [ string range $line [ expr $eq_index + 1 ] [ string length $line ] ] 
      set trimmed_val [ string trim $val ]
      #puts "Key: \"$key\" Val: \"$val\""

      switch $key {
        pkg_name {
          setonce pkg_name $trimmed_val
        }
        pkg_version {
          setonce pkg_version $trimmed_val
        }
        main_script {
          setonce main_script [ file join $manifest_dir $trimmed_val ]
        }
        lib_script {
          lappend lib_scripts [ file join $manifest_dir $trimmed_val ]
        }
        lib_init {
          lappend lib_init_fns $trimmed_val
        }
        lib_include {
          lappend lib_includes $trimmed_val
        }
        lib_object {
          lappend lib_objects $trimmed_val
        }
        linker_libs {
          set linker_libs "$linker_libs $trimmed_val"
        }
        default {
          user_err "Unknown key in manifest file: \"$key\""
        }
      }
    }

    close $manifest
  }

  return [ dict create manifest_dir $manifest_dir \
            pkg_name $pkg_name pkg_version $pkg_version \
            main_script $main_script lib_scripts $lib_scripts \
            lib_init_fns $lib_init_fns lib_includes $lib_includes \
            lib_objects $lib_objects linker_libs $linker_libs ]
}

proc write_deps_file { manifest_dict deps_output_file c_output_file } {
  set deps_output [ open $deps_output_file w ]
  puts -nonewline $deps_output "$deps_output_file $c_output_file :"
  set all_scripts [ dict get $manifest_dict lib_scripts ]
  set main_script [ dict get $manifest_dict main_script ]
  if { $main_script != "" } {
    lappend all_scripts $main_script
  }
  
  foreach script $all_scripts {
    puts -nonewline $deps_output " $script"
  }

  puts $deps_output ""
  close $deps_output
}

proc fill_c_template { manifest_dict resource_var_prefix c_output_file } {
  global SCRIPT_DIR
  global INIT_PKGS_FN
  global MAIN_SCRIPT_STRING
  set pkg_name [ dict get $manifest_dict pkg_name ]
  if { [ string length $pkg_name ] == 0 } {
    set pkg_name "TurbineAppMainPackage"
  }

  set c_template_filename \
    [ file join $SCRIPT_DIR "mkstatic.c.template" ]
  set c_template [ open $c_template_filename ]
  set c_output [ open $c_output_file w ]

  set lib_scripts [ dict get $manifest_dict lib_scripts ]
  set lib_script_vars [ list ]
  foreach lib_script $lib_scripts {
    regsub -all "\[\.-\]" [ file tail $lib_script ] "_" lib_script_var
    set lib_script_var "__turbine_tcl_lib_src_$lib_script_var"
    lappend lib_script_vars $lib_script_var
  }

  puts $c_output "/* AUTOGENERATED FROM $c_template_filename */\n"

  set PLACEHOLDER_RE "@\[a-zA-Z0-9_\]*@"

  while {[gets $c_template line] >= 0} {
    while { [ regexp -indices $PLACEHOLDER_RE $line match_indices ] } {
      # Process first match
      set match_start [ lindex $match_indices 0 ]
      set match_end [ lindex $match_indices 1 ]

      # Text before match
      puts -nonewline $c_output \
        [ string range $line 0 [ expr {$match_start - 1} ] ]
      
      # Sub var name without @ @
      set sub_var_name [ string range $line [ expr {$match_start + 1} ] \
                                            [ expr {$match_end - 1} ] ]

      # Replace variables with appropriate content based on manifest
      switch $sub_var_name {
        USER_HEADERS {
          foreach hdr [ dict get $manifest_dict lib_includes ] {
            puts $c_output "#include <$hdr>"
          }
        }
        TCL_STATIC_PKG_CALLS {
          puts -nonewline $c_output "Tcl_StaticPackage\(NULL, \
                \"${pkg_name}\", ${INIT_PKGS_FN}, ${INIT_PKGS_FN}\);"
        }
        USER_PKG_INIT {
          # Code to init C plus Tcl code for module
          static_pkg_init_code $c_output \
              $INIT_PKGS_FN \
              [ dict get $manifest_dict lib_init_fns ] \
              $lib_script_vars $lib_scripts
        }
        MAIN_SCRIPT_STRING {
          puts -nonewline $c_output $MAIN_SCRIPT_STRING 
        }
        HAS_MAIN_SCRIPT_STRING {
          set main_script [ dict get $manifest_dict main_script ]
          if { [ string length $main_script ] > 0 } {
            puts -nonewline $c_output true
          } else {
            puts -nonewline $c_output false
          }
        }
        MAIN_SCRIPT_DATA {
          # Put main script data in output C file
          global F2A
          set main_script [ dict get $manifest_dict main_script ]
          if { [ string length $main_script ] == 0 } {
            # Output placeholder
            puts $c_output "static const char $MAIN_SCRIPT_STRING\[\] = {0x0};"
          } else {
            set rc [ catch { exec -ignorestderr $F2A \
                -v $MAIN_SCRIPT_STRING -m "static const" $main_script \
                                                        >@ $c_output } ]
            if { $rc } {
              user_err "Error converting tcl file $main_script to C array\
                        in $c_output: $errorInfo"
            }
          }
        }
        RESOURCE_DECLS {
          # iterate through resource files, output declarations
          foreach lib_script_var $lib_script_vars {
            puts $c_output "static const char $lib_script_var\[\];"
          }
        }
        RESOURCE_DATA {
          global F2A
          foreach lib_script_var $lib_script_vars lib_script $lib_scripts {
            set rc [ catch { exec -ignorestderr $F2A -v $lib_script_var \
                     -m "static const" $lib_script >@${c_output} } ]
            if { $rc } {
              user_err "Error converting tcl file $lib_script to C array:\
                        $errorInfo"
            }
          }
        }
        default {
          error "Unknown substitution var \"$sub_var_name\""
        }
      }

      set line [ string range $line [ expr {$match_end + 1} ] \
                                    [ string length $line ] ]
    }
    puts $c_output $line
  }
  close $c_template
  verbose_msg "Created C main file at $c_output_file"
}

# Generate C function to initialize static package
proc static_pkg_init_code { outf init_fn_name lib_init_fns \
                            resource_vars resource_files } {
  puts $outf "static int $init_fn_name\(Tcl_Interp *interp\) {"
  puts $outf "  int rc = TCL_OK;"
  foreach {lib_init_fn} $lib_init_fns {
    puts $outf "  rc = $lib_init_fn\(interp\);"
    puts $outf "  if \(rc != TCL_OK\) {"
    puts $outf "    fprintf\(stderr, \"Error initializing Tcl package: error in $lib_init_fn\"\);"
    puts $outf "    return rc;"
    puts $outf "  }"
  }
 
  foreach var $resource_vars resource_file $resource_files {
    puts $outf "  rc = Tcl_Eval\(interp, $var\);"
    puts $outf "  if \(rc != TCL_OK\) {"
    puts $outf "    fprintf\(stderr, \"Error while loading Tcl code\
                    originally from file %s\\n\","
    puts $outf "                     \"$resource_file\"\);"
    puts $outf "    return rc;"
    puts $outf "  }"
  }
  puts $outf "  return rc;"
  puts $outf "}"
}

proc gen_pkg_index { pkg_index_file pkg_name pkg_version } {
  if { [ string length $pkg_name ] == 0 ||
       [ string length $pkg_version ] == 0 } {
    user_err "Must provide package name and version to generate package index"
  }
  set output [ open $pkg_index_file w ]
  puts $output \
    "package ifneeded $pkg_name $pkg_version {load {} $pkg_name}"
  close $output
  verbose_msg "Created Tcl package index at $pkg_index_file"
}

proc print_link_info { outfile manifest_dict link_objs link_flags } {
  if { $link_objs } {
    puts -nonewline $outfile [ string trim \
        [ dict get $manifest_dict lib_objects ] ]
  }
  
  if { $link_flags } {
    if { $link_objs } {
      puts -nonewline $outfile  " "
    }
    puts -nonewline $outfile [ string trim \
        [ dict get $manifest_dict linker_libs ] ]
  }

  if { $link_objs || $link_flags } {
    # Print newline
    puts $outfile ""
  }
}

proc write_link_deps_file  { manifest_dict output_file link_target_file } {
  set output [ open $output_file w ]
  set objs [ dict get $manifest_dict lib_objects ]
  puts $output "$output_file $link_target_file : $objs"
  close $output
}

proc nonempty { var msg } {
  if { [ string length $var ] == 0 } {
    user_err $msg
  }
}

proc verbose_msg { msg } {
  global verbose_setting
  if { $verbose_setting } {
    puts $msg
  }
}

main
