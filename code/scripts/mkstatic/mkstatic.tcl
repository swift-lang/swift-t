#!/usr/bin/env tclsh

# Copyright 2013 University of Chicago and Argonne National Laboratory
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

# Tcl script to generate entry point C code for statically linked 
# application.  Uses template C code and manifest file to output
# an appropriate C main program.

set SCRIPT_DIR [ file dirname [ info script ] ]
set F2A [ file join $SCRIPT_DIR "file2array.sh" ]

set INIT_PKGS_FN "InitAllPackages"
set MAIN_SCRIPT_STRING "__turbine_tcl_main"

# list of output files to remove on error
set cleanup_error_files [ list ]

proc main { } {
  global SCRIPT_DIR

  set usage "mkstatic.tcl <manifest file> \[-c <output c file>\]\
        \[--include-init-lib <lib directory with Tcl built-ins>\]\
        \[--include-lib <Tcl lib directory with modules/source to include\]\
        \[--tcl-version <Tcl version number for lib selection>\]\
        \[--deps <dependency include for C output> <c output file for deps>\]\
        \[--link-deps <dependency include for linking> <link target>\]\
        \[--link-objs: print list of link objects to stdout\]\
        \[--link-flags: print list of linker library flags to stdout\]\
        \[-r <non-default resource var name>\]\
        \[--ignore-no-manifest: if no manifest present, assume empty manifest\]\
        \[-v: verbose messages to report on process\]\
        \[-h: help\]\n\
        \n\
        Notes: \n\
        -> --include-lib and --include-init-lib behave similarly:\n\
          - both evaluate Tcl source files in the root of the directory,\
            and pkgIndex.tcl files in subdirectories, for later loading.\n\
          - both also look in \${dir}/tcl\${TCL_VERSION}.  This means that\
            --tcl-version must be specified \n\
          - both evaluate init.tcl, if present, before other files\n\
          - --include-init-lib can be specified only once, and those libraries\
            are loaded first \n\
          - --include-init-lib causes the regular Tcl_Init initialization to be\
            skipped, allowing Tcl builtin libraries to be compiled into the\
            result binary \n\
        -> multiple -l flags can be provided to include multiple directories\n\
        -> --link-objs are printed before --link-flags if both provided"

  set non_flag_args [ list ]

  set c_output_file ""
  set deps_output_file ""
  set deps_c_output_file ""
  set link_deps_output_file ""
  set link_deps_target ""
  set print_link_objs 0
  set print_link_flags 0
  set resource_var_prefix "__turbine_tcl_resource_"
  global verbose_setting
  set verbose_setting 0
  set ignore_no_manifest 0
  set skip_tcl_init 0
  set init_lib_dirs [ list ]
  set other_lib_dirs [ list ]
  set tcl_version ""

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
        --include-lib -
        --include-init-lib {
          incr argi
          set lib_dir_arg [ lindex $::argv $argi ]
          nonempty $lib_dir_arg "Expected second non-empty argument to $arg"

          if { $arg == "--include-init-lib" } {
            if { [ llength $init_lib_dirs ] > 0 } {
              user_err "$arg specified more than once"
            }
            lappend init_lib_dirs $lib_dir_arg
            set skip_tcl_init 1
          } else {
            lappend other_lib_dirs $lib_dir_arg
          }
        }
        --tcl-version {
          incr argi
          set tcl_version [ lindex $::argv $argi ]
          nonempty $tcl_version "Expected second non-empty argument to $arg"
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
 
  set init_lib_src [ locate_all_lib_src $tcl_version $init_lib_dirs \
                                     $other_lib_dirs ]

  # generate deps file if needed
  if { [ string length $deps_output_file ] > 0 } {
    #TODO: write_deps_file should include libs
    write_deps_file $manifest_dict $init_lib_src \
                    $deps_output_file $deps_c_output_file
  }

  print_link_info stdout $manifest_dict $print_link_objs $print_link_flags
  
  if { [ string length $link_deps_output_file ] > 0 } {
    write_link_deps_file $manifest_dict $link_deps_output_file $link_deps_target
  }

  if { [ string length $c_output_file ] > 0 } {
    fill_c_template $manifest_dict $skip_tcl_init $init_lib_src \
                    $resource_var_prefix $c_output_file
  }
}

proc user_err { msg } {
  puts stderr "error: $msg"
  cleanup_on_error
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

proc locate_all_lib_src { tcl_version init_lib_dirs other_lib_dirs } {
  # Process with init lib dirs first, others after
  set all_lib_src [ list ]
  foreach lib_dir [ concat $init_lib_dirs $other_lib_dirs ] {
    set lib_src [ locate_lib_src $tcl_version $lib_dir ]
    lappend all_lib_src {*}$lib_src
  }
  verbose_msg "Will include following lib source files: $all_lib_src"
  return $all_lib_src
}

proc locate_lib_src { tcl_version lib_dir } {
  nonempty $tcl_version "Must specify Tcl version to locate libraries\
                         in directories"
  if { ! [ file isdirectory $lib_dir ] } {
    user_err "library directory $lib_dir does not exist"
  }
  # In the Tcl layouts with version-specific subdirectories, that is where
  # the base Tcl functionality is: check those first.
  set check_dirs [ list [ file join $lib_dir "tcl${tcl_version}" ] $lib_dir ]
  set init_tcls [ list ]
  set other_tcls [ list ]
  foreach check_dir $check_dirs {
    verbose_msg "Checking lib directory $check_dir for .tcl files"
    if { ! [ file isdirectory $check_dir ] } {
      continue
    }
    foreach tcl_file [ glob -nocomplain -directory $check_dir "*.tcl" ] {
      set basename [ file tail $tcl_file ]
      if { $basename == "init.tcl" } {
        verbose_msg "Found Tcl init file $tcl_file"
        lappend init_tcls $tcl_file
      } else {
        verbose_msg "Found Tcl lib file $tcl_file"
        lappend other_tcls $tcl_file
      }
    }
    # check subdirectories thereof for pkgIndex.tcl files used to setup
    # packages for later loading
    foreach subdir [ glob -nocomplain -directory $check_dir -type d "*" ] {
      set maybe_pkgindex [ file join $subdir "pkgIndex.tcl" ]
      if [ file exists $maybe_pkgindex ] {
        verbose_msg "Found Tcl package index file $maybe_pkgindex"
        lappend other_tcls $maybe_pkgindex
      }
    }
  }
  return [ concat $init_tcls $other_tcls ]
}

proc write_deps_file { manifest_dict init_lib_src deps_output_file \
                       c_output_file } {
  set deps_output [ open_output_file $deps_output_file ]
  puts -nonewline $deps_output "$deps_output_file $c_output_file :"
  set all_scripts [ dict get $manifest_dict lib_scripts ]
  set main_script [ dict get $manifest_dict main_script ]
  if { $main_script != "" } {
    lappend all_scripts $main_script
  }
  lappend all_scripts {*}$init_lib_src
  
  foreach script $all_scripts {
    puts -nonewline $deps_output " $script"
  }

  puts $deps_output ""
  close $deps_output
}

proc varname_from_file { var_prefix fname used_names } {
  regsub -all "\[\.-\]" [ file tail $fname ] "_" basename
  set basename "${var_prefix}${basename}"
  set name $basename
  set attempt 0
  while { [ lsearch $used_names $name ] >= 0 } {
    incr attempt
    set name "${basename}_${attempt}"
  }
  return $name
}

# Fill in C template
# manifest_dict: data from manifest file
# skip_tcl_init: if true, skip regular Tcl_Init function
# init_lib_src: library files to load in order after initializing interp
# resource_var_prefix: prefix to apply to resource vars
proc fill_c_template { manifest_dict skip_tcl_init init_lib_src \
                      resource_var_prefix c_output_file } {
  global SCRIPT_DIR
  global INIT_PKGS_FN
  global MAIN_SCRIPT_STRING
  global F2A

  set pkg_name [ dict get $manifest_dict pkg_name ]
  set pkg_version [ dict get $manifest_dict pkg_version ]
 
 if { [ string length $pkg_name ] == 0 } {
    set pkg_name "TurbineUserPackage"
  }
  if { [ string length $pkg_version ] == 0 } {
    set pkg_version "0.0"
  }

  set c_template_filename \
    [ file join $SCRIPT_DIR "mkstatic.c.template" ]
  set c_template [ open $c_template_filename ]
  set c_output [ open_output_file $c_output_file ]

  set all_vars [ list ]
  
  set init_lib_src_vars [ list ]
  foreach src_file $init_lib_src {
    set init_lib_src_var [ varname_from_file $resource_var_prefix \
                                             $src_file $all_vars ]
    lappend init_lib_src_vars $init_lib_src_var
    lappend all_vars $init_lib_src_var
  }

  set lib_scripts [ dict get $manifest_dict lib_scripts ]
  set lib_script_vars [ list ]
  foreach lib_script $lib_scripts {
    set lib_script_var [ varname_from_file $resource_var_prefix \
                                           $lib_script $all_vars ]
    lappend lib_script_vars $lib_script_var 
    lappend all_vars $lib_script_var
  }

  set all_src_vars [ concat $init_lib_src_vars $lib_script_vars ]
  set all_src_files [ concat $init_lib_src $lib_scripts ]

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
        SKIP_TCL_INIT {
          # Output integer to use as truth value
          puts -nonewline $c_output $skip_tcl_init
        }
        TCL_LIB_INIT {
          tcl_lib_init $c_output $init_lib_src_vars $init_lib_src
        }
        REGISTER_USER_PKGS {
          register_pkg $c_output $pkg_name $pkg_version $INIT_PKGS_FN
        }
        USER_PKG_INIT {
          # Code to init C plus Tcl code for module
          user_pkg_init_code $c_output \
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
          set main_script [ dict get $manifest_dict main_script ]
          if { [ string length $main_script ] == 0 } {
            # Output placeholder
            puts $c_output "static const char $MAIN_SCRIPT_STRING\[\] = {0x0};"
          } else {
            set rc [ catch { exec -ignorestderr $F2A \
                -v $MAIN_SCRIPT_STRING -m "static const" $main_script \
                                                        >@ $c_output } ]
            if { $rc } {
              user_err "could not convert tcl file $main_script to C array\
                        in $c_output_file"
            }
          }
        }
        MAIN_SCRIPT_FILE {
          # Output main script file name, or "" if not present
          puts -nonewline $c_output [ dict get $manifest_dict main_script ]
        }
        RESOURCE_DECLS {
          # iterate through resource files, output declarations
          foreach var $all_src_vars src_file $all_src_files {
            puts $c_output "static const char $var\[\];\
                            /* $src_file */"
          }
        }
        RESOURCE_DATA {
          foreach src_var $all_src_vars src_file $all_src_files {
            puts $c_output "/* data from $src_file */"
            set rc [ catch { exec -ignorestderr $F2A -v $src_var \
                     -m "static const" $src_file >@${c_output} } ]
            if { $rc } {
              user_err "could not convert tcl file $src_file to C array"
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

proc tcl_lib_init { outf init_lib_vars init_lib_src } {

  foreach var $init_lib_vars src_file $init_lib_src {
    # Move to new line
    puts $outf ""

    # set $dir var that pkgIndex.tcl expect to point to pkg
    # directory containing loadable package
    if { [ file tail $src_file ] == "pkgIndex.tcl" } {
      puts $outf "  Tcl_SetVar(interp, \"dir\",\
                      \"[file dirname $src_file]\", 0);"
    }
    eval_resource_var $outf $var $src_file
  }
  # Clear dir variable in case it was set
  puts $outf "  Tcl_UnsetVar(interp, \"dir\", 0);"
}

proc register_pkg { outf pkg_name pkg_version init_pkg_fn } {
  puts $outf "Tcl_StaticPackage\(NULL, \
        \"${pkg_name}\", ${init_pkg_fn}, ${init_pkg_fn}\);"
  puts $outf "  int _rc;"
  puts $outf "  _rc = Tcl_Eval(interp, \n\
        \"[pkg_ifneeded $pkg_name $pkg_version]\");"
  puts $outf "  if (_rc != TCL_OK) {"
  puts $outf "    fprintf(stderr, \
                \"Could not initialize $pkg_name\");"
  puts $outf "    Tcl_Eval(interp, \"puts \$::errorInfo\");"
  puts $outf "    exit(1);"
  puts $outf "  }"
}

# Generate C function to initialize static package
proc user_pkg_init_code { outf init_fn_name lib_init_fns \
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
    eval_resource_var $outf $var $resource_file
  }
  puts $outf "  return rc;"
  puts $outf "}"
}

# generate code to evaluate resource variable and return TCL return code
# from C function upon failure
proc eval_resource_var { outf var resource_file } {
  puts $outf "  rc = Tcl_Eval\(interp, $var\);"
  puts $outf "  if \(rc != TCL_OK\) {"
  puts $outf "    fprintf\(stderr, \"Error while loading Tcl code\
                  originally from file %s:\\n\","
  puts $outf "                     \"$resource_file\"\);"
  puts $outf "    Tcl_Eval(interp, \"puts \$::errorInfo\");"
  puts $outf "    return rc;"
  puts $outf "  }"
}

proc pkg_ifneeded { pkg_name pkg_version } {
  return "package ifneeded {$pkg_name} {$pkg_version} {load {} {$pkg_name}}"
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
  set output [ open_output_file $output_file ]
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

# Open script output file and register in case of error
proc open_output_file { path } {
  set fd [ open $path w ]
  add_cleanup_file [ list $fd $path ]
  return $fd
}

proc add_cleanup_file { path } {
  global cleanup_error_files
  lappend cleanup_error_files $path
}

proc cleanup_on_error { } {
  global cleanup_error_files
  foreach cleanup_file $cleanup_error_files {
    file delete $cleanup_file
  }
  set cleanup_error_files [ list ]
}

if { [ catch main ] } {
  cleanup_on_error
  puts stderr "$::errorInfo"
}
