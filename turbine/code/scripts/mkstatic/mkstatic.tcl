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

source [ file join $SCRIPT_DIR ".." ".." "lib" "helpers.tcl" ]

set INIT_PKGS_FN "InitAllPackages"
set MAIN_SCRIPT_STRING "__turbine_tcl_main"

# list of output files to remove on error
set cleanup_error_files [ list ]

proc main { } {
  global SCRIPT_DIR

  set usage "mkstatic.tcl <manifest file> \[-c <output c file>\]\
        \[--main-script <tcl main script, overriding manifest \]\
        \[--include-sys-lib <lib directory with Tcl built-ins>\]\
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
        -> --include-lib and --include-sys-lib behave similarly:\n\
          - both evaluate Tcl source files in the root of the directory,\
            and pkgIndex.tcl files in subdirectories, for later loading.\n\
          - both also look in \${dir}/tcl\${TCL_VERSION}.  This means that\
            --tcl-version must be specified \n\
          - both evaluate init.tcl, if present, before other files\n\
          - --include-sys-lib can be specified only once, and those libraries\
            are loaded first \n\
          - --include-sys-lib causes the regular Tcl_Init initialization to be\
            skipped, allowing Tcl builtin libraries to be compiled into the\
            result binary \n\
        -> multiple ---include-lib flags can be provided to include multiple directories\n\
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
  set sys_lib_dirs [ list ]
  set other_lib_dirs [ list ]
  set tcl_version ""
  set main_script_override ""

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
        --main-script {
          incr argi
          set main_script_override [ lindex $::argv $argi ]
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
        --include-sys-lib {
          incr argi
          set lib_dir_arg [ lindex $::argv $argi ]
          nonempty $lib_dir_arg "Expected second non-empty argument to $arg"

          if { $arg == "--include-sys-lib" } {
            if { [ llength $sys_lib_dirs ] > 0 } {
              user_err "$arg specified more than once"
            }
            lappend sys_lib_dirs $lib_dir_arg
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
    if { $ignore_no_manifest } {
      set manifest_filename ""
    } else {
      user_err "Expected exactly one non-flagged argument for manifest file " \
                "but received: [ something $non_flag_args ]"
    }
  } else {
    set manifest_filename [ lindex $non_flag_args 0 ]
  }

  set manifest_dict [ read_manifest $manifest_filename $ignore_no_manifest $main_script_override ]

  set all_lib_src [ locate_all_lib_src $tcl_version $sys_lib_dirs \
                                        $other_lib_dirs ]

  # generate deps file if needed
  if { [ string length $deps_output_file ] > 0 } {
    write_deps_file $manifest_dict $all_lib_src \
                    $deps_output_file $deps_c_output_file
  }

  print_link_info stdout $manifest_dict $print_link_objs $print_link_flags

  if { [ string length $link_deps_output_file ] > 0 } {
    write_link_deps_file $manifest_dict $link_deps_output_file $link_deps_target
  }

  set sys_lib_dir ""
  if { $skip_tcl_init } {
    if { [ llength $sys_lib_dirs ] == 0 } {
      error "Internal error: zero-length sys_lib_dirs"
    } elseif { [ llength $sys_lib_dirs ] > 1 } {
      error "Internal error: multiple sys_lib_dirs"
    }
    set sys_lib_dir [ lindex $sys_lib_dirs 0 ]
  }

  if { [ string length $c_output_file ] > 0 } {
    fill_c_template $manifest_dict $tcl_version $skip_tcl_init $sys_lib_dir \
                    $all_lib_src $resource_var_prefix $c_output_file
  }
}

proc user_err { args } {
  puts stderr "mkstatic: error: [ concat {*}$args ]"
  cleanup_on_error
  exit 1
}

proc user_warn { msg } {
  puts stderr "warning: $msg"
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
proc read_manifest { manifest_filename ignore_no_manifest main_script_override } {
  # Initial values of things that may be specified by manifest file
  set manifest_dir [ file dirname $manifest_filename ]

  # Tcl package info
  set pkg_name ""
  set pkg_version ""

  set main_script ""

  # lib scripts are executed before main script
  set lib_scripts [ list ]

  # bundled files to be extracted
  set bundled_files [ list ]

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
          if { ! [ string equal $trimmed_val "" ] } {
            lappend lib_scripts [ file join $manifest_dir $trimmed_val ]
          }
        }
        bundled_file {
          if { ! [ string equal $trimmed_val "" ] } {
            lappend bundled_files [ file join $manifest_dir $trimmed_val ]
          }
        }
        lib_init {
          if { ! [ string equal $trimmed_val "" ] } {
            lappend lib_init_fns $trimmed_val
          }
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

  if { $main_script_override != "" } {
    set main_script $main_script_override
  }

  return [ dict create manifest_dir $manifest_dir \
            pkg_name $pkg_name pkg_version $pkg_version \
            main_script $main_script lib_scripts $lib_scripts \
            bundled_files $bundled_files \
            lib_init_fns $lib_init_fns lib_includes $lib_includes \
            lib_objects $lib_objects linker_libs $linker_libs ]
}

# Find Tcl source files that should be loaded in directory, including:
# - Plain Tcl source files with .tcl extensions to be evaled
# - Tcl packages with pkgIndex.tcl files to be indexed
# - Tcl modules with .tm extensions to be bundled and loaded on demand
#
# Result is list of lists.  Each list has two forms:
# FILE src_file
# PACKAGE name version (src_file)*
# The first is a .tcl file that should be sourced at startup
# The second is a package that should be registered at startup for later loading
proc locate_all_lib_src { tcl_version sys_lib_dirs other_lib_dirs } {
  # Process with init lib dirs first, others after
  set all_lib_src [ list ]
  foreach lib_dir [ concat $sys_lib_dirs $other_lib_dirs ] {
    set lib_src [ locate_lib_src $tcl_version $lib_dir ]
    lappend all_lib_src {*}$lib_src

    if { [ llength $lib_src ] == 0 } {
      warn_msg "No Tcl library source located in $lib_dir"
    }
  }
  verbose_msg "Will include following lib Tcl source files: $all_lib_src"
  return $all_lib_src
}

proc locate_lib_src { tcl_version lib_dir } {
  nonempty $tcl_version "Must specify Tcl version to locate libraries\
                         in directories. Provided lib dir: $lib_dir"

  set tcl_version_parts [ split $tcl_version "." ]
  set tcl_version_major [ lindex $tcl_version_parts 0 ]
  set tcl_version_minor [ lindex $tcl_version_parts 1 ]
  if { ! [ string is integer -strict $tcl_version_major ] ||
       ! [ string is integer -strict $tcl_version_minor ] } {
    user_err "Expected integral major and minor components in tcl \
              version: $tcl_version"
  }

  if { ! [ file isdirectory $lib_dir ] } {
    user_err "library directory $lib_dir does not exist"
  }

  set check_dirs [ list ]
  # In the Tcl layouts with version-specific subdirectories, that is where
  # the base Tcl functionality is: check those first.
  lappend check_dirs [ file join $lib_dir "tcl${tcl_version}" ]

  # Loadable Tcl modules are in a version-specific subdirectory.
  # We can load any modules with the same major version and equal or
  # lower minor version
  # Might be in major-version subdir
  lappend check_dirs [ file join $lib_dir "tcl${tcl_version_major}" ]
  # Might be in major-version/minor-version subdir
  for { set minor $tcl_version_minor } { $minor >= 0 } { incr minor -1 } {
    lappend check_dirs [ file join $lib_dir "tcl${tcl_version_major}" \
                                   "${tcl_version_major}.${minor}" ]
  }
  lappend check_dirs $lib_dir

  # Accumulate libs in found
  set found [ list ]
  foreach check_dir $check_dirs {
    verbose_msg "Checking lib directory $check_dir for .tcl and .tm files"
    if { ! [ file isdirectory $check_dir ] } {
      continue
    }

    foreach tcl_file [ glob -nocomplain -directory $check_dir "*.tcl" ] {
      verbose_msg "Found Tcl lib file $tcl_file"
      lappend found [ list FILE $tcl_file ]
    }

    foreach tcl_file [ glob -nocomplain -directory $check_dir "*.tm" ] {
      verbose_msg "Found Tcl module lib file $tcl_file"
      lassign [ tm_package_version $tcl_file ] tm_package tm_version
      lappend found [ list PACKAGE $tm_package $tm_version $tcl_file ]
    }

    # check subdirectories thereof for pkgIndex.tcl files used to setup
    # packages for later loading
    foreach subdir [ glob -nocomplain -directory $check_dir -type d "*" ] {
      set maybe_pkgindex [ file join $subdir "pkgIndex.tcl" ]
      if [ file exists $maybe_pkgindex ] {
        verbose_msg "Found Tcl package index file $maybe_pkgindex"

        set pkgindex_infos [ pkgindex_analyse $maybe_pkgindex ]
        if { [ llength $pkgindex_infos ] > 0 } {
          foreach pkg_info $pkgindex_infos {
            lappend found [ list PACKAGE {*}$pkg_info ]
          }
        } else {
          # Just load the index at startup
          lappend found [ list FILE $maybe_pkgindex ]
        }
      }
    }
  }

  # Sort according to order for tcl version
  return [ lsort -command [ list lib_init_order $tcl_version ] $found ]
}

# Try to analyse pkgIndex.tcl file to see what source files it loads
# Returns: a list of source-only packages (i.e. ones which just source
#           Tcl files to load). Only returned if *all* packages are
#           source-only, otherwise return empty list
# Each list entry is of form: (package_name, package_version, (src_file)*
proc pkgindex_analyse { f } {
  set pkgs_info [ pkgindex_tryload $f ]
  set src_packages [ list ]
  foreach pkg_info $pkgs_info {
    lassign $pkg_info name version init_script
    set src_files [ pkg_analyse $name $version $init_script ]
    if { [ llength $src_files ] > 0 } {
      verbose_msg "$f had a source-only package: $name $version with\
                   files $src_files"
      lappend src_packages [ list $name $version {*}$src_files ]
    } else {
      verbose_msg "Could not analyse init script for $name $version:\n$init_script\n"
      return ""
    }
  }
  return $src_packages
}

# return index in core module list, or -1 if not present
proc core_module_ix { lib } {
  if { [ lindex $lib 0 ] != "FILE" } {
    return -1;
  }
  set basename [ file tail [ lindex $lib 1 ] ]
  set core_modules [ list init.tcl package.tcl tm.tcl ]
  return [ lsearch -exact $core_modules $basename ]
}

proc lib_init_order { tcl_version lib1 lib2 } {
  # Prioritize scripts in correct order:
  # - First basic initialization
  set lib_type1 [ lindex $lib1 0 ]
  set lib_type2 [ lindex $lib2 0 ]

  set core_ix1 [ core_module_ix $lib1 ]
  set core_ix2 [ core_module_ix $lib2 ]

  if { $core_ix1 >= 0 } {
    if { $core_ix2 >= 0 && $core_ix2 <= $core_ix1 } {
      return 1
    } else {
      return -1
    }
  } elseif { $core_ix2 >= 0 } {
    return 1
  }

  # Then setup package/module subsystem and register packages/modules
  if { $lib_type1 == "PACKAGE" } {
    if { $lib_type2 == "PACKAGE" } {
      # Break ties based on package name and version
      return [ string compare $lib1 $lib2 ]
    } else {
      return -1
    }
  } elseif { $lib_type2 == "PACKAGE" } {
    return 1
  }

  # Then load other scripts in some deterministic order
  set file1 [ lindex $lib1 1 ]
  set file2 [ lindex $lib2 1 ]
  set basename1 [ file tail $file1 ]
  set basename2 [ file tail $file2 ]

  # If not a special name, alphabetical order of basename
  set basename_compare [ string compare $basename1 $basename2 ]
  if { $basename_compare != 0 } {
    return $basename_compare
  }

  # Otherwise check directory name (e.g. for pkgIndex.tcl)
  return [ string compare [ file dir $file1 ] [ file dir $file2 ] ]
}


# Analyse a package.
# Return empty string if not successful
# Return list of source command args if successful
proc pkg_analyse { name version init_script } {
  # TODO: naive parsing into commands that may give incorrect results
  # sometimes, but should be good enough for 99% of cases (we can
  # just revert to doing nothing in cases where it doesn't work)
  set subs [ list "\\\n" " " ";" "\n" ]
  set init_script_cmds [ split [ string map $subs $init_script ] "\n" ]
  set source_args [ list ]
  foreach cmd $init_script_cmds {
    set cmd [ string trim $cmd ]
    if { [ string length $cmd ] == 0 } {
      continue
    }

    if { ! [ string is list $cmd ] } {
      # Could not tokenise command
      return ""
    }

    set cmdname [ lindex $cmd 0 ]
    if { $cmdname == "source" } {
      lappend source_args [ lrange $cmd 1 [ llength $cmd ] ]
    } else {
      # Don't know about this command
      # TODO: we could also analyse tclPkgSetup command
      verbose_msg "Don't know about $cmdname"
      return ""
    }
  }
  return $source_args
}

proc pkgindex_tryload { f } {
  set int [ interp create -safe ]

  set share_procs [ list pkgindex_tryload_child ]
  foreach share_proc $share_procs {
    interp alias $int $share_proc  {} $share_proc
  }

  set res [ interp eval $int "
    return \[ pkgindex_tryload_child $f \]
  "]

  interp delete $int
  return $res
}

# Load a pkgindex file and return a list of info about packages loaded.
# Each list entry is a sublist with (name, version, init script)
proc pkgindex_tryload_child { f } {
  set dir [ file dir $f ]
  set before_names [ package names ]

  source $f

  set loaded_pkgs [ list ]
  foreach name [ package names ] {
    if { [ lsearch -exact $before_names $name ] < 0 } {
      # this was newly loaded by the pkgIndex.tcl file
      foreach version [ package versions $name ] {
        set script [ package ifneeded $name $version ]
        lappend loaded_pkgs [ list $name $version $script ]
      }
    }
  }

  return $loaded_pkgs
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

  foreach lib $init_lib_src {
    set lib_type [ lindex $lib 0 ]
    if { $lib_type == "FILE" } {
      lappend all_scripts [ lindex $lib 1 ]
    } elseif { $lib_type == "PACKAGE" } {
      set scripts [ lrange $lib 3 [ llength $lib ] ]
      lappend all_scripts {*}scripts
    } else {
      error "unexpected type $lib_type in $lib"
    }
  }

  foreach script $all_scripts {
    puts -nonewline $deps_output " $script"
  }

  puts $deps_output ""
  close $deps_output
}

proc file_base_varname { fname } {
  regsub -all "\[\.-\]" [ file tail $fname ] "_" result
  return $result
}

proc varname_from_file { var_prefix fname used_names } {
  set basename "${var_prefix}[ file_base_varname ${fname} ]"
  set name $basename
  set attempt 0
  while { [ lsearch $used_names $name ] >= 0 } {
    incr attempt
    set name "${basename}_${attempt}"
  }
  return $name
}

proc bundled_file_basename { path } {
  return [ file tail $path ]
}

# Fill in C template
# manifest_dict: data from manifest file
# skip_tcl_init: if true, skip regular Tcl_Init function
# init_lib_src: library files to load in order after initializing interp
# resource_var_prefix: prefix to apply to resource vars
proc fill_c_template { manifest_dict tcl_version skip_tcl_init sys_lib_dir \
                      init_lib_src \
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

  verbose_msg "making C file: $c_output_file for: " \
              "package $pkg_name $pkg_version"

  set all_vars [ list ]

  # List of package info: {name version}
  # Empty list/string if not a package
  set init_lib_package_info [ list ]
  set init_lib_src_vars [ list ]
  set init_lib_src_files [ list ]
  # TODO: will need to track packages separately
  foreach lib $init_lib_src {
    set lib_type [ lindex $lib 0 ]
    if { $lib_type == "FILE" } {
      set src_files [ list [ lindex $lib 1 ] ]
      set package_info ""
    } elseif { $lib_type == "PACKAGE" } {
      set src_files [ lrange $lib 3 [ llength $lib ] ]
      set package_info [ list [ lindex $lib 1 ] [ lindex $lib 2 ] ]
    } else {
      error "Unexpected lib type $lib_type in $lib"
    }
    set src_vars [ list ]
    foreach src_file $src_files {
      set src_var [ varname_from_file $resource_var_prefix \
                                      $src_file $all_vars ]
      lappend src_vars $src_var
      lappend all_vars $src_var
    }
    lappend init_lib_package_info $package_info
    lappend init_lib_src_files $src_files
    lappend init_lib_src_vars $src_vars
  }

  set lib_scripts [ dict get $manifest_dict lib_scripts ]
  set lib_script_vars [ list ]
  foreach lib_script $lib_scripts {
    set lib_script_var [ varname_from_file $resource_var_prefix \
                                           $lib_script $all_vars ]
    lappend lib_script_vars $lib_script_var
    lappend all_vars $lib_script_var
  }

  set bundled_files [ dict get $manifest_dict bundled_files ]
  set bundled_basenames [ list ]
  set bundled_vars [ list ]
  foreach bundled_file $bundled_files {
    set bundled_var [ varname_from_file $resource_var_prefix \
                                            $bundled_file $all_vars ]
    lappend bundled_vars $bundled_var
    lappend all_vars $bundled_var

    set bundled_basename [ bundled_file_basename $bundled_file ]
    if { [ lsearch -exact $bundled_basenames $bundled_basename ] >= 0 } {
      user_err "Duplicate base names for bundled files: $bundled_basename"
    }
  }

  set all_src_vars [ concat $init_lib_src_vars {*}$lib_script_vars ]
  set all_src_files [ concat $init_lib_src_files {*}$lib_scripts ]

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
        TCL_CUSTOM_PRE_INIT {
          tcl_custom_preinit $c_output $skip_tcl_init $tcl_version $sys_lib_dir
        }
        TCL_LIB_INIT {
          tcl_lib_init $c_output $init_lib_package_info $init_lib_src_vars \
                       $init_lib_src_files $skip_tcl_init $sys_lib_dir
        }
        REGISTER_USER_PKGS {
          register_pkg $c_output $pkg_name $pkg_version $INIT_PKGS_FN
        }
        PKG_INIT_FNS {
          # Functions to init C plus Tcl code for modules
          # Generate needed init functions for library modules
          gen_lib_init_fns $c_output $init_lib_package_info \
                           $init_lib_src_vars $init_lib_src_files

          # Initialize package with user code
          gen_pkg_init_fn $c_output \
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
          verbose_msg "ingesting main script:" $main_script
          if { [ string length $main_script ] == 0 } {
            # Output placeholder
            puts $c_output "static const unsigned char $MAIN_SCRIPT_STRING\[\] = {0x0};"
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
            puts $c_output "static const unsigned char $var\[\];\
                            /* $src_file */"
            puts $c_output "static const size_t ${var}_len;"
          }

          foreach var $bundled_vars bundled_file $bundled_files {
            puts $c_output "static const unsigned char $var\[\];\
                            /* $bundled_file */"
            puts $c_output "static const size_t ${var}_len;"
          }

          puts $c_output "static const unsigned char *bundled_file_data\[\] = {"
          foreach var $bundled_vars {
            puts $c_output "$var, "
          }
          puts $c_output "};"

          puts $c_output "static const size_t *bundled_file_lens\[\] = {"
          foreach var $bundled_vars {
            puts $c_output "&${var}_len, "
          }
          puts $c_output "};"

          puts $c_output "static const char *bundled_file_names\[\] = {"
          foreach bundled_file $bundled_files {
            puts $c_output "\"[ bundled_file_basename $bundled_file ]\", "
          }
          puts $c_output "};"
        }
        BUNDLED_FILES_COUNT {
          puts -nonewline $c_output [ llength $bundled_files ]
        }
        BUNDLED_FILE_NAMES {
          puts -nonewline $c_output "bundled_file_names"
        }
        BUNDLED_FILE_DATA {
          puts -nonewline $c_output "bundled_file_data"
        }
        BUNDLED_FILE_LENS {
          puts -nonewline $c_output "bundled_file_lens"
        }
        RESOURCE_DATA {
          # TODO: Ctrl-D in .tm file not handled right - .tm files can
          # in theory use it as a separator to append binary data at end
          # of file. It is not clear if such .tm files occur in the wild
          # and the Tcl standard library doesn't include any as of writing
          foreach src_var $all_src_vars src_file $all_src_files {
            verbose_msg "ingesting source: $src_var $src_file"
            puts $c_output "/* data from $src_file */"
            set rc [ catch { exec -ignorestderr $F2A -v $src_var \
                     -m "static const" $src_file >@${c_output} } ]
            if { $rc } {
              user_err "could not convert tcl file $src_file to C array"
            }
          }

          foreach var $bundled_vars bundled_file $bundled_files {
            verbose_msg "ingesting file: $var $bundled_file"
            puts $c_output "/* data from $bundled_file */"
            set rc [ catch { exec -ignorestderr $F2A -v $var \
                     -m "static const" $bundled_file >@${c_output} } ]
            if { $rc } {
              user_err "could not convert file $bundled_file to C array"
            }
          }
        }
        default {
          error "Unknown substitution var \"$sub_var_name\""
        }
      }

      # Text after match
      set line [ string range $line [ expr {$match_end + 1} ] \
                                    [ string length $line ] ]
    }
    puts $c_output $line
  }
  close $c_template
  verbose_msg "Created C main file at $c_output_file"
}

# Set any required variables in interpreter prior to running init.tcl
# script.  These variables aren't officially documented, so we'll check
# the Tcl version to see if it's one we've encountered before
proc tcl_custom_preinit { outf skip_tcl_init tcl_version sys_lib_dir } {

  if { $skip_tcl_init } {
    # Regular Tcl init not happening- check we can support this
    if { [ string length $tcl_version ] == 0 } {
      user_err "Tcl version required when including init lib"
    }

    if { ! [ catch [ package vcompare $tcl_version 0 ] ] } {
      user_err "Invalid version number: \"$tcl_version\""
    }

    # Versions that we've tested with
    set min_ver 8.5
    set max_ver 8.6
    if { [ package vcompare $tcl_version $min_ver ] < 0 } {
      user_warn "Do not officially support including initialization library\
                 for Tcl version $tcl_version < $min_ver. This may work but\
                 do not be surprised if Tcl initialization fails!"
    }

    if { [ package vcompare $tcl_version $max_ver ] > 0 } {
      user_warn "Do not officially support including initialization library\
                 for Tcl version $tcl_version > $max_ver. This may work but\
                 do not be surprised if Tcl initialization fails!"
    }
  }

  # Set library search path to tcl system library directory
  puts $outf "  Tcl_ObjSetVar2(interp, Tcl_NewStringObj(\"tcl_library\", -1),\
                NULL, Tcl_NewStringObj(\"\", -1), 0);"
}

# Helper to put Tcl_Eval call into output C code
proc tcl_eval { outf script } {
  set script [ string map { "\n" "\\n\\\n" "\"" "\\\"" } $script ]
  puts $outf "  Tcl_Eval(interp, \"$script\");"
}

proc tcl_custom_postinit { outf skip_tcl_init sys_lib_dir } {
  if { $skip_tcl_init } {
    # Reset auto_path to just including user libraries
    tcl_eval $outf {
      if {[info exists env(TCLLIBPATH)]} {
          set auto_path $env(TCLLIBPATH)
      } else {
          set auto_path ""
      }
    }
  }
}

# Generate init functions for Tcl library modules
proc gen_lib_init_fns { outf package_infos init_lib_vars init_lib_src } {
  puts $outf ""
  foreach package_info $package_infos vars $init_lib_vars \
          src_files $init_lib_src {
    if { [ llength $package_info ] == 0 } {
      # not a package
      continue
    }

    # Packages are registered to be loaded on demand: need init function
    lassign $package_info pkg_name pkg_version
    gen_pkg_init_fn $outf [ pkg_init_fn_name $pkg_name $pkg_version ] \
                    [ list ] $vars $src_files
  }
}

# Run the specified library scripts in the interpreter
# pkgIndex.tcl files are handled specially
proc tcl_lib_init { outf package_infos init_lib_vars init_lib_src \
                    skip_tcl_init sys_lib_dir } {

  puts $outf ""
  foreach package_info $package_infos vars $init_lib_vars \
          src_files $init_lib_src {
    # Move to new line
    puts $outf ""

    if { [ llength $package_info ] == 0 } {
      # Plain source files get evaluated immediately
      foreach var $vars src_file $src_files {
        set basename [ file tail $src_file ]
        if { $basename == "pkgIndex.tcl" } {
          # set $dir var that pkgIndex.tcl expect to point to pkg
          # directory containing loadable package
          puts $outf "  Tcl_SetVar(interp, \"dir\",\
                          \"[file dirname $src_file]\", 0);"
        }
        eval_resource_var $outf $var $src_file
        if { [ file tail $src_file ] == "init.tcl" } {
          tcl_custom_postinit $outf $skip_tcl_init $sys_lib_dir
        }
      }
    } else {
      lassign $package_info pkg_name version
      # Packages are registered to be loaded on demand
      register_pkg $outf $pkg_name $version \
                         [ pkg_init_fn_name $pkg_name $version ]
    }
  }
  # Clear dir variable in case it was set
  puts $outf "  Tcl_UnsetVar(interp, \"dir\", 0);"
}

# Parse into package name and version, return as list
proc tm_package_version { src_file } {
  set basename [ file tail $src_file ]
  set ext [ file extension $basename ]
  if { $ext != ".tm" } {
    error "Expected .tm extension for $basename"
  }
  set module_name [ file rootname $basename ]
  # module_name is of form ${package_name}-${package_version}
  set module_parts [ split $module_name "-" ]
  if { [ llength $module_parts ] != 2 } {
    error "Expected .tm name \"$module_name\" to consist of name-version\
          with no other \"-\" separators"
  }
  return $module_parts
}
proc pkg_init_fn_name { pkg_name version } {
  regsub -all {\.} ${version} "_" version2
  return "init_${pkg_name}_${version2}"
}

proc register_pkg { outf pkg_name pkg_version init_pkg_fn } {
  # use static-pkg library function
  # move to new line
  puts $outf ""
  puts $outf "  if (register_static_pkg(interp, \"$pkg_name\", \
                    \"$pkg_version\", $init_pkg_fn) != TCL_OK) {"
  # register_static_pkg prints error message
  puts $outf "    return TCL_ERROR;"
  puts $outf "  }"
}

# Generate C function to initialize static package
proc gen_pkg_init_fn { outf init_fn_name lib_init_fns \
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
# from C function upon failure. Assumes that there is a matching var,
# ${var}_len that has length of source
proc eval_resource_var { outf var resource_file } {
  puts $outf "  if \(tcl_eval_bundled_file(interp, (const char*)$var, \
                      (int)${var}_len, \"$resource_file\") != TCL_OK) {"
  # Error info printing handled in function
  puts $outf "    return TCL_ERROR;"
  puts $outf "  }"
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

proc verbose_msg { args } {
  global verbose_setting
  if { $verbose_setting } {
    puts "mkstatic: [ concat {*}$args ]"
  }
}

proc warn_msg { msg } {
  puts stderr "WARN: $msg"
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
    verbose_msg "deleted: $cleanup_file"
  }
  set cleanup_error_files [ list ]
}

if { [ catch main ] } {
  cleanup_on_error
  puts stderr "$::errorInfo"
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 2
# End:
