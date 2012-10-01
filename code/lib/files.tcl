# Filesystem related functions

namespace eval turbine {

    # Extract file status future from handle
    proc get_file_status { file_handle } {
      return [ lindex $file_handle 0 ]
    }

    # Extract filename future from handle
    proc get_file_path { file_handle } {
      return [ lindex $file_handle 1 ]
    }

    # return tcl bool value
    proc is_file_mapped { file_handle } {
      return [ lindex $file_handle 2 ]
    }

    # get the filename from the file handle
    proc filename2 { stack out in } {
      set file_handle [ lindex $in 0 ]
      copy_string NOSTACK $out [ get_file_path $file_handle ]
    }

    # get the filename if mapped, assign if unmapped
    proc get_output_file_path { file_handle } {
      if { ! [ is_file_mapped $file_handle ] } {
        set mapping [ init_unmapped $file_handle ]
        debug "Mapping <${file_handle}> to ${mapping}"
      }
      return [ get_file_path $file_handle ]
    }

    proc input_file { stack out filepath } {
      set outfile [ lindex $out 0 ]
      set mapped [ is_file_mapped $outfile ]
      if { $mapped } {
        error "file \[ $outfile \] was already mapped, cannot use input_file"
      }
      rule "input_file-$outfile-$filepath" "$filepath" \
            $turbine::LOCAL [ list input_file_body $outfile $filepath ]
    }

    proc input_file_body { outfile filepath } {
      set filepath_val [ retrieve_string $filepath ]
      input_file_impl $outfile $filepath_val
    }
    
    proc input_file_impl { outfile filepath_val } {
      if { ! [ file exists $filepath_val ] } {
        error "input_file: file $filepath_val does not exist"
      }
      store_string [ get_file_path $outfile ] $filepath_val
      store_void [ get_file_status $outfile ]
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
      set filename [ mktemp ]
      store_string [ get_file_path $file_handle ] $filename
      return $filename
    }

    # Copy one file to another.  If the destination is unmapped,
    # then shortcut by just making sure they point to the same place
    proc copy_file { parent outputs inputs } {
      set dst [ lindex $outputs 0 ]
      set src [ lindex $inputs 0 ]
      puts "dst: $dst"
      set mapped [ is_file_mapped $dst ]
      if { $mapped } {
        # is mapped, so need to make sure that file is at mapped path
        set dstpath [ get_file_path $dst ]
        set srcpath [ get_file_path $src ]
        set srcstatus [ get_file_status $src ]
        rule "copy_file-$dst-$src" "$dstpath $srcpath $srcstatus" \
            $turbine::WORK [ list copy_file_body $dst $src ]
      } else {
        # not mapped.  As shortcut, just make them both point to the 
        # same file and update status once src file is closed
        copy_void NO_STACK [ get_file_status $dst ] [ get_file_status $src ]
        copy_string NO_STACK [ get_file_path $dst ] [ get_file_path $src ]
      }
    }

    proc copy_file_body { dst src } {
      set dstpath [ get_file_path $dst ]
      set dstpath_val [ retrieve_string $dstpath ]
      set srcpath [ get_file_path $src ]
      set srcpath_val [ retrieve_string $srcpath ]
      # do the copy: srcpath to dstpath
      # TODO: is this the best way to do this?
      file copy -force $srcpath_val $dstpath_val
      # signal that output is now available
      store_void [ get_file_status $dst ]
    }

    # return the filename of a unique temporary file
    proc mktemp {} {
      #TODO: do something better!
      set id [ expr int(100000000 * rand()) ]
      return "/tmp/turbine-${id}"
    }

    proc close_file { handle } {
      store_void [ get_file_status $handle ]  
    }

}
