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

# Turbine FILES.TCL

# Filesystem related functions
# TODO: Need some Turbine tests for this

namespace eval turbine {
    namespace export get_file_status get_file_path is_file_mapped       \
        allocate_file                                                   \
        set_filename_val get_filename_val                               \
        filename2 copy_file close_file file_read file_write             \
        swift_filename                                                  \
        create_file store_file                                          \
        create_file_ref store_file_ref                                  \
        retrieve_file_ref retrieve_decr_file_ref acquire_file_ref       \
        retrieve_file retrieve_decr_file store_file

    # Initialize file struct types, should be called when initializing
    # Turbine
    proc init_file_types { } {
      # Setup file and file_ref struct types
      # File is represented by path.  Status of td reflects whether file
      # data available
      adlb::declare_struct_type 0 file [ list path string ]
      adlb::declare_struct_type 1 file_ref \
                [ list file ref is_mapped integer ]
    }

    # usage: <name> <is_mapped> [<create props>]
    # is_mapped: if true, file mapping will be set;
    #            if false, temporary file can be generated
    proc allocate_file { name is_mapped {read_refcount 1} {write_refcount 1} args } {
        # use void to signal file availability
        set file_td [ allocate_custom "$name" file \
                              $read_refcount $write_refcount {*}${args} ]
        set handle [ make_file_td $file_td $is_mapped ]
        upvar 1 $name v
        set v $handle
        return $handle
    }

    # Initialize a file td and return a file handle
    proc make_file_td { file_td is_mapped } {
        # Format matches file_ref struct type
        return [ dict create file $file_td is_mapped $is_mapped ]
    }

    # Initialize a list of file tds and return file handles
    proc make_file_tds { file_tds is_mappeds } {
      set handles [ list ]
      foreach file_td $file_tds is_mapped $is_mappeds {
        lappend handles [ make_file_td $file_td $is_mapped ]
      }
      return $handles
    }

    proc file_handle_from_td { td is_mapped } {
      return [ dict create file $td is_mapped $is_mapped ]
    }

    proc create_file { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id file $read_refcount \
                              $write_refcount $permanent ]
    }

    # Handles files that are input to a builtin function
    # Increments reference count to avoid file deletion
    # Returns just the file name for handoff to user code
    proc swift_filename { file_var } {
        upvar $file_var file_handle
        incr_local_file_refcount $file_var 2
        set result [ lindex $file_handle 0 ]
        return $result
    }

    proc get_file_td { file_handle } {
      return [ dict get $file_handle file ]
    }

    # Extract file status handle from file handle
    proc get_file_status { file_handle } {
      # Create handle for subscript of struct variable
      return [ get_file_td $file_handle ]
    }

    # Extract filename future from handle
    proc get_file_path { file_handle } {
      # Create handle for subscript of struct variable (first elem)
      return [ adlb::subscript_struct [ get_file_td $file_handle ] 0 ]
    }

    # return tcl bool value
    proc is_file_mapped { file_handle } {
      return [ dict get $file_handle is_mapped ]
    }

    proc store_file_from_local { file_handle local_f_varname } {
       upvar 1 $local_f_varname local_f
       # Increment refcount so not cleaned up locally
       lset local_f 1 [ expr {[ lindex $local_f 1 ] + 1} ]
       store_void [ get_file_status $file_handle ]
    }

    # store file and update local file var refcounts
    # second argument must be var name so we can manipulate refcounts
    proc store_file { file_handle local_f_varname {set_filename 1} } {
      upvar 1 $local_f_varname local_f
      # Increment refcount so not cleaned up locally
      lset local_f 1 [ expr {[ lindex $local_f 1 ] + 1} ]

      set value [ dict create path [ local_file_path $local_f ] ]

      if { $set_filename } {
        set id [ get_file_td $file_handle ]
        log "store: <$id>=$value"
        adlb::store $id file $value
        c::cache_store $id file $value
      } else {
        # Close without modifying filename
        close_file $file_handle
      }
    }

    proc retrieve_file { file_handle {cachemode CACHED} {decrref 0} } {
        set id [ get_file_td $file_handle ]
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id $decrref
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref file ]
            } else {
              set result [ adlb::retrieve $id file ]
            }

            if { $cache } {
              c::cache_store $id file $result
            }
        }
        debug "retrieve: <$id>=$result"
        return [ create_local_file_ref [ dict get $result path ] 2 \
                                   [ is_file_mapped $file_handle ] ]
    }

    proc retrieve_decr_file { file_handle {cachemode CACHED} } {
      return [ retrieve_file $file_handle $cachemode 1 ]
    }

    proc create_file_ref { id {read_refcount 1} {write_refcount 1} \
                             {permanent 0} } {
        return [ adlb::create $id file_ref $read_refcount \
                              $write_refcount $permanent ]
    }

    proc store_file_ref { id value {store_read_refs 1} {store_write_refs 0}} {
        log "store: <$id>=$value"
        adlb::store $id file_ref $value $store_read_refs $store_write_refs
        c::cache_store $id file_ref $value
    }

    proc retrieve_file_ref { id {cachemode CACHED} {decrref 0} } {
        set cache [ string equal $cachemode CACHED ]
        if { $cache && [ c::cache_check $id ] } {
            set result [ c::cache_retrieve $id ]
            if { $decrref } {
              read_refcount_decr $id
            }
        } else {
            if { $decrref } {
              set result [ adlb::retrieve_decr $id $decrref file_ref ]
            } else {
              set result [ adlb::retrieve $id file_ref ]
            }

            if { $cache } {
              c::cache_store $id file_ref $result
            }
        }
        debug "retrieve: <$id>=$result"
        return $result
    }

    proc retrieve_decr_file_ref { id {cachemode CACHED} } {
      return [ retrieve_file_ref $id $cachemode 1 ]
    }

    proc acquire_file_ref { id {incrref 1} {decrref 0} } {
        set result [ adlb::acquire_ref $id file_ref $incrref $decrref ]
        debug "acquire_file_ref: <$id>=$result"
        return $result
    }

    # get the filename from the file handle
    proc filename2 { out in } {
      set file_handle [ lindex $in 0 ]
      copy_string $out [ get_file_path $file_handle ]
    }

    # Copy in filename from future
    proc copy_in_filename { file filename } {
      rule $filename "copy_in_filename_body {$file} $filename"
    }

    proc copy_in_filename_body { file filename } {
      set filename_val [ retrieve_decr_string $filename ]
      set_filename_val $file $filename_val
    }

    # get the filename if mapped, assign if unmapped
    proc get_output_file_path { file_handle } {
      if { ! [ is_file_mapped $file_handle ] } {
        set mapping [ init_unmapped $file_handle ]
        debug "Mapping <${file_handle}> to ${mapping}"
      }
      return [ get_file_path $file_handle ]
    }

    # Helper that can be used in place of rule that handles
    # mapped and unmapped output files.
    # Will make sure that output files are mapped before executing cmd
    # msg: log msg
    # waitfor: list of regular inputs
    # outfiles: output files, will wait correctly for these
    # infiles: input files, will wait correctly for these
    # target: Where to send work e.g. $turbine::WORK
    # cmd: command to execute when closed
    proc rule_file_helper { msg waitfor outfiles infiles target cmd } {

        foreach outfile $outfiles {
            if { [ is_file_mapped $outfile ] } {
                # Wait for mapping to be ready
                set outpath [ get_file_path $outfile ]
                lappend waitfor $outpath
            } else {
                # Assign temporary mapping
                init_unmapped $outfile
            }
        }

        foreach infile $infiles {
            # Wait for file to be closed, not just path
            lappend waitfor [ get_file_td $infile ]
        }
        rule $waitfor $cmd  name $msg target $target
    }

    proc input_file { out filepath } {
      set outfile [ lindex $out 0 ]
      set mapped [ is_file_mapped $outfile ]
      if { $mapped } {
        set outfile_path [ get_file_path $outfile ]

        # Copy to mapped output
        rule "$outfile_path $filepath" \
          [ list input_file_copy_body $outfile $filepath ] \
          name "input_file_copy-$outfile-$filepath"
      } else {
        rule "$filepath" [ list input_file_body $outfile $filepath ] \
          name "input_file-$outfile-$filepath"
      }
    }

    proc input_file_copy_body { outfile filepath } {
      set outfile_path_val [ get_filename_val $outfile ]
      set filepath_val [ retrieve_decr_string $filepath ]
      physical_file_copy $outfile_path_val $filepath_val
      close_file $outfile
    }

    proc input_file_body { outfile filepath } {
      set filepath_val [ retrieve_decr_string $filepath ]
      input_file_impl $outfile $filepath_val
    }

    proc input_file_impl { outfile filepath_val } {
      if { ! [ file exists $filepath_val ] } {
          turbine_error "input_file: file '$filepath_val' does not exist"
      }
      # Set filename and close
      set_filename_val $outfile $filepath_val 1
    }

    # fname: filename as tcl string
    # return: local file handle
    proc input_file_local { outf_varname fname } {
      upvar 1 $outf_varname outf
      if { ! [ file exists $fname ] } {
          turbine_error "input_file: file '$fname' does not exist"
      }

      set outf_path [ local_file_path $outf ]
      set outf_mapped [ local_file_mapped $outf ]

      if { $outf_mapped } {
        physical_file_copy $outf_path $fname
        # Increment file refcount to prevent deletion of file
        incr_local_file_refcount outf 1
      } else {
        set outf [ create_local_file_ref $fname 100 ]
      }
    }

    proc input_url { out filepath } {
      set outfile [ lindex $out 0 ]

      set mapped [ is_file_mapped $outfile ]
      if { $mapped } {
        set outfile_path [ get_file_path $outfile ]
        rule "$outfile_path $filepath" \
          [ list input_url_error_body $outfile_path $filepath ] \
          name "input_url_error-$outfile-$filepath"
      } else {
        rule "$filepath" [ list input_url_body $outfile $filepath ] \
          name "input_url-$outfile-$filepath"
      }
    }

    proc input_url_body { outfile filepath } {
      set filepath_val [ retrieve_decr_string $filepath ]
      input_url_impl $outfile $filepath_val
    }

    proc input_url_error_body { out_url_path in_url_path } {
      set in_url_path_val [ retrieve_decr_string $in_url_path ]
      set out_url_path_val [ retrieve_string $out_url_path ]
      input_url_mapped_error $out_url_path_val $in_url_path_val
    }

    proc input_url_impl { outfile filepath_val } {
      set mapped [ is_file_mapped $outfile ]
      if { $mapped } {
        error "Copying $filepath_val to mapped url"
      }

      # Set filename and close
      set_filename_val $outfile $filepath_val 1
    }

    proc input_url_mapped_error { out_url in_url } {
        error "Cannot do url-to-url copy from $in_url to $out_url:\
              url-to-url copies are not supported"
    }

    proc input_url_local { out_url_varname url } {
      upvar 1 $out_url_varname out_url

      set out_url_mapped [ local_file_mapped $out_url ]

      if { $out_url_mapped } {
        set out_url_path [ local_file_path $out_url ]
        input_url_mapped_error $out_url_path $url
      }

      # Create local file ref with extra refcount so that it is never deleted
      set out_url [ create_local_file_ref $url 100 ]
    }

    # initialise an unmapped file to a temporary location
    # should be called if a function is writing to an unmapped file
    # assigns the filename future in the file_handle, and also
    # returns the chosen filename
    proc init_unmapped { file_handle } {
      set mapped [ is_file_mapped $file_handle ]
      if { $mapped } {
        error "argument error: called init_unmapped on mapped file:
              <$file_handle>"
      }
      set filename [ mktemp_impl ]
      set_filename_val $file_handle $filename
      return $filename
    }

    # Copy one file to another.  If the destination is unmapped,
    # then shortcut by just making sure they point to the same place
    proc copy_file { outputs inputs } {
      set dst [ lindex $outputs 0 ]
      set src [ lindex $inputs 0 ]
      log "copy_file $src => $dst"
      #  puts "dst: $dst"
      set mapped [ is_file_mapped $dst ]
      set src_td [ get_file_td $src ]
      if { $mapped } {
        # is mapped, so need to make sure that file is at mapped path
        set dstpath [ get_file_path $dst ]
        rule [ list $dstpath $src_td ] \
            "copy_file_body {$dst} {$src}" \
            name "copy_file-{$dst}-{$src}" type $::turbine::WORK
      } else {
        # not mapped.  As shortcut, just make them both point to the
        # same file and update status once src file is closed
        rule $src_td [ list copy_file_td_body $dst $src ]
      }
    }

    proc copy_file_td_body { dst src } {
      set tmp [ retrieve_decr_file $src ]
      store_file $dst tmp
    }

    proc copy_file_body { dst src } {
      set dstpath_val [ get_filename_val $dst ]
      set src_val [ retrieve_decr_file $src ]
      set srcpath_val [ local_file_path $src_val ]
      # do the copy: srcpath to dstpath
      physical_file_copy $dstpath_val $srcpath_val
      # signal that output is now available
      close_file $dst
    }

    # Implement physical file copy
    proc physical_file_copy { dstpath srcpath } {
      # TODO: is this the best way to do this?
      log "physical file copy \"$srcpath\" => \"$dstpath\""
      ensure_directory_exists2 $dstpath
      file copy -force $srcpath $dstpath
    }

    proc copy_local_file_contents { dst src } {
      set dstpath [ local_file_path $dst ]
      set srcpath [ local_file_path $src ]
      ensure_directory_exists2 $dstpath
      file copy -force $srcpath $dstpath
    }

    proc dereference_file { v r } {
        rule $r "dereference_file_body {$v} $r" \
            name "dereference_file"
    }
    proc dereference_file_body { v r } {
        # Get the TD from the reference
        set handle [ acquire_file_ref $r 1 1 ]
        copy_file [ list $v ] [ list $handle ]
    }

    variable mktemp_files

    proc mktemp_string { output input } {
        # No input!
        rule "" "mktemp_body $output" name "mktemp-$output"
    }
    proc mktemp_body { output } {
        set filename [ mktemp_impl ]
        store_string $output $filename
    }

    # Return the filename of a unique temporary file
    proc mktemp_impl { } {
        global env
        variable mktemp_files
        if { [ info tclversion ] >= 8.6 } {
            if [ info exists env(SWIFT_TMP) ] {
                set tmpdir $env(SWIFT_TMP)
            } elseif [ info exists env(TMP) ] {
                set tmpdir $env(TMP)
            } else {
                set tmpdir "/tmp"
            }
            try {
                # Subcommand tempfile returns an fd: we do not use it
                close [ file tempfile result $tmpdir/.turbine ]
            } on error e {
                turbine_error "Error creating temporary file!\n $e"
            }
        } else {
            # Tcl 8.5 or older: fall back on system mktemp command
            # TODO: re-add this argument (#364): --suffix=.turbine
            if [ string equal $env(TURBINE_MAC) "no" ] {
                set result [ exec mktemp --suffix=.turbine ]
            } else {
                set result [ exec mktemp -t turbine.XXXXXX ]
            }
        }
        # If we are not deleting temp files, we log them so the user
        # can keep track of them
        getenv_integer "SWIFT_TMP_AUTODELETE" 1 delete
        if { ! $delete } { log "mktemp: $result" }
        lappend mktemp_files $result
        return $result
    }

    proc mktemp_cleanup { } {
        variable mktemp_files
        getenv_integer "SWIFT_TMP_AUTODELETE" 1 delete
        if { ! $delete } return
        if [ info exists mktemp_files ] {
            foreach f $mktemp_files {
              # Note: this does not raise error if file not present
              file delete $f
            }
        }
    }

    # set the filename to a string
    proc set_filename_val { file_handle filename {write_decr 0}} {
      adlb::insert [ get_file_td $file_handle ] 0 $filename string $write_decr
    }

    proc get_filename_val { file_handle {read_decr 0} } {
      # TODO: different struct retrieve function?
      return [ adlb::lookup [ get_file_td $file_handle ] 0 $read_decr ]
    }

    proc close_file { handle } {
      write_refcount_decr [ get_file_td $handle ]
    }

    proc file_read_refcount_incr { handle { amount 1 } } {
      read_refcount_incr [ get_file_td $handle ] $amount
    }

    proc file_read_refcount_decr { handle { amount 1 } } {
      file_read_refcount_incr $handle [ expr {$amount * -1} ]
    }

    proc do_glob { result inputs } {
        rule $inputs "do_glob_body $result $inputs" \
             name "glob-$result" type $::turbine::WORK
    }
    proc do_glob_body { result s } {
        set s_value [ retrieve_decr_string $s ]
        set r_value [ ::glob -nocomplain $s_value ]
        log "do_glob: $s_value"

        set i 0
        foreach v $r_value {
            # Allocate file with given filename
            # TODO: this binds lots of variable names in this scope
            #       - sorta bad
            set f [ allocate_file "<$result>\[$i\]" 1 ]
            # Set filename and close file in one operation
            set_filename_val $f $v 1
            container_insert $result $i $f file_ref
            incr i
        }
        # close container
        write_refcount_decr $result
    }

    # Create a reference to track local file
    proc create_local_file_ref { filepath {refcount 1} {mapped 1} } {
        # puts "create_local_file_ref $filepath $refcount"
        return [ list $filepath $refcount $mapped ]
    }

    proc local_file_path { local_file } {
        return [ lindex $local_file 0 ]
    }

    proc local_file_mapped { local_file } {
        return [ lindex $local_file 2 ]
    }

    proc set_file { f local_f_varname } {
       upvar 1 $local_f_varname local_f
       # Increment refcount so not cleaned up locally
       lset local_f 1 [ expr {[ lindex $local_f 1 ] + 1} ]
       # Decrement write refcount to close file
       close_file $f
    }

    proc incr_local_file_refcount { varname levels } {
        upvar $levels $varname v
        set old_refcount [ lindex $v 1 ]
        set new_refcount [ expr {$old_refcount + 1} ]

        if [ expr $old_refcount <= 0  ] {
          error "Trying to increment reference count from zero or negative: \
                    [ local_file_path $v ]"
        }
        lset v 1 $new_refcount
    }

    proc decr_local_file_refcount { varname } {
        upvar 1 $varname v
        set new_refcount [ expr {[ lindex $v 1 ] - 1} ]
        # puts "decr_local_file_refcount $varname={$v} new=$new_refcount"
        lset v 1 $new_refcount
        if { $new_refcount == 0 } {
            set path [ lindex $v 0 ]
            log "delete locally used file $path"
            file delete -force $path
        }
    }

    proc file_read { outputs inputs } {
        rule_file_helper "read_file-$inputs" [ list ] \
            [ list ] $inputs \
            $::turbine::WORK \
            "file_read_body $outputs $inputs "
    }

    proc file_read_body { result src } {
	set s [ get_filename_val $src 1 ]
        set fp [ ::open $s r ]
	set file_data [ read $fp ]
        close $fp
	store_string $result $file_data
    }

    proc file_read_local { local_file } {
        set fp [ ::open [ local_file_path $local_file ] r ]
	set file_data [ read $fp ]
        close $fp
        return $file_data
    }

    proc file_write { outputs inputs } {
	rule_file_helper "write_file" $inputs \
            $outputs [ list ] \
	    $::turbine::WORK \
            "file_write_body $outputs $inputs"
    }

    proc file_write_body { dst str } {
        set str_val [ retrieve_decr_string $str ]
	set d [ get_filename_val $dst ]
        file_write_local d $str_val
	close_file $dst
    }

    # local_file: local file object
    # data: data to write to file
    # TODO: calling convention not figured out yet
    proc file_write_local { local_file_name data } {
        upvar $local_file_name local_file
        ensure_directory_exists2 $local_file
	set fp [ ::open [ local_file_path $local_file ] w+ ]
        puts -nonewline $fp $data
	close $fp
    }

    proc blob_read { result input } {
	set src [ lindex $input 0 ]
        rule_file_helper "blob_read-$result-$src" [ list ] \
            [ list ] [ list $src ] \
            $::turbine::WORK \
            [ list blob_read_body $result $src ]
    }
    proc blob_read_body { result src } {
	set val [ retrieve_decr_file $src ]
	set input_name [ local_file_path $val ]

        set blob [ new_turbine_blob ]
        log "blob_read: $input_name"
        blobutils_read $input_name $blob
        set ptr [ blobutils_cast_to_long \
                      [ turbine_blob_pointer_get $blob ] ]
        set length [ turbine_blob_length_get  $blob ]
        log "blob_read: length: $length"
	store_blob $result [ list $ptr $length ]
        blobutils_destroy $blob
    }

    proc blob_write_local { local_file_name blob } {
        set ptr    [ lindex $blob 0 ]
        set length [ lindex $blob 1 ]
        set b [ blobutils_create $ptr $length ]
        upvar $local_file_name local_file
        set f [ local_file_path $local_file ]
        log "blob_write: $f $length"
        blobutils_write $f $b
        delete_turbine_blob $b
    }

    proc blob_hdf_write_local { local_file_name entry blob } {
        set ptr    [ lindex $blob 0 ]
        set length [ lindex $blob 1 ]
        set b [ blobutils_create $ptr $length ]
        upvar $local_file_name local_file
        set name [ local_file_path $local_file ]
        set rc [ blobutils_hdf_write $name $entry $b ]
        delete_turbine_blob $b
        if { ! $rc } {
            turbine_error "HDF write failed to: $name"
        }
    }

    # input_file: local file representation
    proc file_lines_impl { input_file comment } {
        set input_name [ local_file_path $input_file ]
        set fp [ ::open $input_name r ]
        set line_number 0
        set lines [ dict create ]
        # Comments are disabled if the comment string is empty
        set comments_enabled [ string length $comment ]
        while { [ gets $fp line ] >= 0 } {
            if $comments_enabled {
                regsub "${comment}.*" $line "" line
            }
            set line [ string trim $line ]
            if { [ string length $line ] > 0 } {
                dict append lines $line_number $line
            }
            incr line_number
        }
        close $fp
        return $lines
    }

    # input_file: local file representation
    proc file_chunks_impl { input_file delimiter comment } {
        set input_name [ local_file_path $input_file ]
        set fp [ ::open $input_name r ]
        set chunk_number 0
        set chunks [ dict create ]
        # Comments are disabled if the comment string is empty
        #                          or just whitespace
        set comment [ string trim $comment ]
        set comments_enabled [ string length $comment ]
        while { ! [ eof $fp ] } {
            set chunk ""
            while { [ gets $fp line ] >= 0 } {
                if $comments_enabled {
                    regsub "${comment}.*" $line "" line
                }

                if { $line eq $delimiter } break

                if { [ string length $line ] > 0 } {
                    if { $chunk ne "" } {
                        append chunk "\n"
                    }
                    append chunk $line
                }
            }
            set chunk [ string trim $chunk ]
            dict append chunks $chunk_number $chunk
            incr chunk_number
        }
        close $fp
        return $chunks
    }

    proc file_mtime_impl { filename } {
        if [ catch { set result [ file mtime $filename ] } e ] {
            turbine_error "file_mtime(): $e"
        }
        return $result
    }

    proc write_array_string { outputs inputs } {
        rule $inputs "write_array_string_body $outputs $inputs"
    }
    proc write_array_string_body { output a chunk } {

        # Set up chunks
        set chunk_value [ retrieve_integer $chunk ]
        set size [ adlb::container_size $a ]

        # Set up output file
        set d [ get_filename_val $output ]
        ensure_directory_exists2 $d
	set fp [ ::open $d w+ ]

        log "write_array_string: file=$d chunk=$chunk_value"

        for { set i 0 } { $i < $size } { incr i $chunk_value } {
            set D [ adlb::enumerate $a dict $chunk_value $i ]
            set count [ dict size $D ]
            log "write_array_string: count=$count"
            dict for { k v } $D {
                # show k v
                puts $fp "$k $v"
            }
        }
	close $fp
	close_file $output
    }

    proc write_array_string_ordered { outputs inputs } {
        rule $inputs "write_array_string_ordered_body $outputs $inputs"
    }
    proc write_array_string_ordered_body { output a } {

        set size [ adlb::container_size $a ]

        # Set up output file
        set d [ get_filename_val $output ]
        ensure_directory_exists2 $d
	set fp [ ::open $d w+ ]

        log "write_array_string_ordered: file=$d"

        for { set i 0 } { $i < $size } { incr i } {
            set v [ adlb::lookup $a $i ]
            log "write_array_string_ordered: i=$i"
            puts $fp "$v"
        }
	close $fp
	close_file $output
    }
}

# Local Variables:
# mode: tcl
# tcl-indent-level: 4
# End:
