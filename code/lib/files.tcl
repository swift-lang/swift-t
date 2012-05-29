# Filesystem related functions

namespace eval turbine {

    # Extract file status future from handle
    proc _filestatus { file_handle } {
      return [ lindex $file_handle 0 ]
    }

    # Extract filename future from handle
    proc _filepath { file_handle } {
      return [ lindex $file_handle 1 ]
    }

    # return tcl bool value
    proc _is_mapped { file_handle } {
      return [ lindex $file_handle 2 ]
    }

    # get the filename from the file handle
    proc filename2 { stack out in } {
      set file_handle [ lindex $in 0 ]
      copy_string NOSTACK $out [ _filepath $file_handle ]
    }

    proc input_file { stack out filepath } {
      set outfile [ lindex $out 0 ]
      puts "\[ $outfile \] $filepath"
      set mapped [ _is_mapped $outfile ]
      puts $mapped
      if { $mapped } {
        error "file \[ $outfile \] was already mapped, cannot use input_file"
      }
      rule "input_file-$outfile-$filepath" "$filepath" \
            $turbine::LOCAL [ list input_file_body $outfile $filepath ]
    }

    proc input_file_body { outfile filepath } {
      set filepath_val [ retrieve_string $filepath ]
      if { ! [ file exists $filepath_val ] } {
        error "input_file: file $filepath_val does not exist"
      }
      store_string [ _filepath $outfile ] $filepath_val
      store_void [ _filestatus $outfile ]
    }

    # initialise an unmapped file to a temporary location
    # should be called if a function is writing to an unmapped file
    # assigns the filename future in the file_handle, and also
    # returns the chosen filename
    proc init_unmapped { file_handle } {
      set mapped [ _is_mapped $file_handle ]
      if { $mapped } {
        error "argument error: called init_unmapped on mapped file:
              <$file_handle>"
      }
      set filename [ mktemp ]
      store_string [ _filepath $file_handle ] $filename
      return $filename
    }

    # Copy one file to another.  If the destination is unmapped,
    # then shortcut by just making sure they point to the same place
    proc copy_file { parent outputs inputs } {
      set dst [ lindex $outputs 0 ]
      set src [ lindex $inputs 0 ]
      set mapped [ _is_mapped $dst ]
      if { $mapped } {
        # is mapped, so need to make sure that file is at mapped path
        set dstpath [ _filepath $dst ]
        set srcpath [ _filepath $src ]
        set srcstatus [ _filestatus $src ]
        rule "copy_file-$dst-$src" "$dstpath $srcpath $srcstatus" \
            $turbine::WORK [ list copy_file_body $dst $src ]
      } else {
        # not mapped.  As shortcut, just make them both point to the 
        # same file and update status once src file is closed
        copy_void NO_STACK [ _filestatus $dst ] [ _filestatus $src ]
        copy_string NO_STACK [ _filepath $dst ] [ _filepath $src ]
      }
    }

    proc copy_file_body { dst src } {
      set dstpath [ _filepath $dst ]
      set dstpath_val [ retrieve_string $dstpath ]
      set srcpath [ _filepath $src ]
      set srcpath_val [ retrieve_string $srcpath ]
      # do the copy: srcpath to dstpath
      # TODO: is this the best way to do this?
      file copy -force $srcpath_val $dstpath_val
      # signal that output is now available
      store_void [ _filestatus $dst ]
    }

    # return the filename of a unique temporary file
    proc mktemp {} {
      return "TODO"
    }

}
