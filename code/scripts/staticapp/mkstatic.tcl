# Tcl script to generate entry point C code for statically linked 
# application.  Uses template C code and manifest file to output
# an appropriate C main program.

set SCRIPT_DIR [ file dirname [ info script ] ]

set INIT_PKGS_FN "InitAllPackages"
set MAIN_SCRIPT_STRING "turbine_main_script"

proc main { } {
  global SCRIPT_DIR
  # TODO: compile/link options?
  set usage "mkstatic.tcl <manifest file> \[-c <output c file> \] \
            \[-p <pkgIndex.tcl file> \] \[-h <tcl code resource header> \] \
            \[-r <non-default resource var name> \] \
            -R: generate resource files for lib_script entries"

  set non_flag_args [ list ]

  # TODO: proper arg processing
  set c_output_file ""
  set resource_header ""
  set pkg_index ""
  set gen_resources 0
  set resource_var_prefix ""

  for { set argi 0 } { $argi < $::argc } { incr argi } {
    set arg [ lindex $::argv $argi ]
    if { [ string index $arg 0 ] == "-" } {
      switch $arg {
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
        -h {
          incr argi
          set resource_header [ lindex $::argv $argi ]
          nonempty $resource_header "Expected non-empty argument to -h"
        }
        -r {
          incr argi
          set resource_var_prefix [ lindex $::argv $argi ]
          nonempty $resource_var_prefix "Expected non-empty argument to -r"
        }
        -R {
          set gen_resources 1
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

  set manifest_dict [ read_manifest $manifest_filename ]
 
  # TODO: generate deps file if appropriate

  # generate resource files if requested
  if { $gen_resources } {
    if { [ string length $resource_header ] == 0 } {
      user_err "Must provide resource header to generate resource files"
    }
    gen_resource_files [ dict get $manifest_dict lib_scripts ] \
                       $resource_header $resource_var_prefix
  }

  if { [ string length $c_output_file ] > 0 } {
    if { [ string length $resource_header ] == 0 } {
      user_err "Must provide resource header to generate output C file"
    }
    fill_c_template $manifest_dict $resource_header $resource_var_prefix\
                    $c_output_file
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
proc read_manifest { manifest_filename } {
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

  # Compiler/linker flags
  set CFLAGS ""
  set LDFLAGS ""

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
      CFLAGS {
        set CFLAGS "$CFLAGS $trimmed_val"
      }
      LDFLAGS {
        set LDFLAGS "$LDFLAGS $trimmed_val"
      }
      default {
        user_err "Unknown key in manifest file: \"$key\""
      }
    }
  }

  close $manifest

  return [ dict create manifest_dir $manifest_dir \
            pkg_name $pkg_name pkg_version $pkg_version \
            main_script $main_script lib_scripts $lib_scripts \
            lib_init_fns $lib_init_fns lib_includes $lib_includes \
            lib_objects $lib_objects linker_libs $linker_libs \
            CFLAGS $CFLAGS LDFLAGS $LDFLAGS ]
}

proc fill_c_template { manifest_dict resource_hdr resource_var_prefix \
                       c_output_file } {
  global SCRIPT_DIR
  global INIT_PKGS_FN
  global MAIN_SCRIPT_STRING
  set pkg_name [ dict get $manifest_dict pkg_name ]

  set c_template_filename \
    [ file join $SCRIPT_DIR "turbine_user_script.c.template" ]
  set c_template [ open $c_template_filename ]
  set c_output [ open $c_output_file w ]

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
          # TODO: path for resource_hdr
          puts $c_output "#include \"$resource_hdr\""
        }
        TCL_STATIC_PKG_CALLS {
          puts -nonewline $c_output "Tcl_StaticPackage\(NULL, \
                \"${pkg_name}\", ${INIT_PKGS_FN}, ${INIT_PKGS_FN}\);"
        }
        USER_PKG_INIT {
          # Code to init C plus Tcl code for module
          # TODO: need to generate resource files beforehand
          static_pkg_init_code $c_output \
              $INIT_PKGS_FN \
              [ dict get $manifest_dict lib_init_fns ] \
              "${resource_var_prefix}" \
              "${resource_var_prefix}_len" \
              "${resource_var_prefix}_init" \
              "${resource_var_prefix}_filenames"
        }
        MAIN_SCRIPT_STRING {
          puts -nonewline $c_output $MAIN_SCRIPT_STRING 
        }
        MAIN_SCRIPT_DATA {
          # Put main script data in output C file
          global SCRIPT_DIR
          # TODO: fixup paths from manifest file
          set main_script [ dict get $manifest_dict main_script ]
          set rc [ catch { exec -ignorestderr \
                [ file join $SCRIPT_DIR "file2array.sh" ] \
                $main_script $MAIN_SCRIPT_STRING >@ $c_output } ]
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
  puts "Created C main file at $c_output_file"
}

# Generate C function to initialize static package
proc static_pkg_init_code { outf init_fn_name lib_init_fns \
      resource_var resource_var_len resource_var_init \
      resource_var_files } {
  puts $outf "static int $init_fn_name\(Tcl_Interp *interp\) {"
  puts $outf "  int rc;"
  foreach {lib_init_fn} $lib_init_fns {
    puts $outf "  rc = $lib_init_fn\(interp\);"
    puts $outf "  if \(rc != TCL_OK\) {"
    puts $outf "    fprintf\(stderr, \"Error initializing Tcl package: error in $lib_init_fn\"\);"
    puts $outf "    return rc;"
    puts $outf "  }"
  }

  puts $outf "  $resource_var_init\(\);"
  puts $outf "  for (int i = 0; i < $resource_var_len; i++ ) {"
  puts $outf "    const char *tclcode = $resource_var\[i\];"
  puts $outf "    rc = Tcl_Eval\(interp, tclcode\);"
  puts $outf "    if \(rc != TCL_OK\) {"
  puts $outf "      fprintf\(stderr, \"Error while loading Tcl file %s\\n\","
  puts $outf "        $resource_var_files\[i\]\);"
  puts $outf "      return rc;"
  puts $outf "    }"
  puts $outf "  }"
  puts $outf "}"
}

proc gen_resource_files { lib_scripts resource_header \
                          resource_var_prefix } {
  global SCRIPT_DIR

  set lib_script_cs [ list ]
  foreach lib_script $lib_scripts {
    set lsl [ string length $lib_script ]
    if { [ string range $lib_script [ expr { $lsl - 4 } ] $lsl ] \
                                    == ".tcl" } {
      set lib_script_c \
        "[ string range ${lib_script}_tcl.c 0 [ expr {$lsl-5} ] ]_tcl.c"
    } else {
      set lib_script_c $lib_script.c
    }
    lappend lib_script_cs $lib_script_c
    
    regsub -all "\[\.-\]" [ file tail $lib_script ] "_" lib_script_var
    set rc [ catch { exec -ignorestderr \
          [ file join $SCRIPT_DIR "file2array.sh" ] \
          $lib_script $lib_script_var > $lib_script_c } ]
    if { $rc } {
      user_err "Error converting tcl file $lib_script to C array\
                in $lib_script_c: $errorInfo"
    }
    puts "Created C resource file at $lib_script_c"
  }

  set rc [ catch { exec -ignorestderr \
        [ file join $SCRIPT_DIR "files2arrays_mkhdr.sh" ] \
        $resource_var_prefix {*}$lib_script_cs > $resource_header  } ]
  if { $rc } {
    user_err "building index of C resource files ($lib_script_cs =>\
              $resource_header) failed"
  }
  puts "Created resource header at $resource_header"
}

proc gen_pkg_index { pkg_index_file pkg_name pkg_version } {
  set output [ open $pkg_index_file w ]
  puts $output \
    "package ifneeded $pkg_name $pkg_version {load {} $pkg_name}"
  close $output
  puts "Created Tcl package index at $pkg_index_file"
}

proc nonempty { var msg } {
  if { [ string length $var ] == 0 } {
    user_err $msg
  }
}

main
