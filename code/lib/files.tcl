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
    namespace export get_file_status get_file_path is_file_mapped \
                     filename2 copy_file close_file file_read file_write
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
    proc filename2 { out in } {
      set file_handle [ lindex $in 0 ]
      copy_string $out [ get_file_path $file_handle ]
      read_refcount_decr [ get_file_status $file_handle ]
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
    # waitfor: list of regular
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
        # Wait for both path and status
	set inpath [ get_file_path $infile ]
	set instatus [ get_file_status $infile ]
        lappend waitfor $inpath $instatus
      }
        rule $waitfor $cmd  name $msg target $target
    }

    proc input_file { out filepath } {
      set outfile [ lindex $out 0 ]
      set mapped [ is_file_mapped $outfile ]
      if { $mapped } {
          error "file \[ $outfile \] was already mapped, cannot use input_file"
      }
      rule "$filepath" [ list input_file_body $outfile $filepath ] \
        name "input_file-$outfile-$filepath"
    }

    proc input_file_body { outfile filepath } {
      set filepath_val [ retrieve_decr_string $filepath ]
      input_file_impl $outfile $filepath_val
    }

    proc input_file_impl { outfile filepath_val } {
      if { ! [ file exists $filepath_val ] } {
        error "input_file: file '$filepath_val' does not exist"
      }
      store_string [ get_file_path $outfile ] $filepath_val
      store_void [ get_file_status $outfile ]
    }

    # fname: filename as tcl string
    # return: local file handle
    proc input_file_local { fname } {
      if { ! [ file exists $fname ] } {
        error "input_file: file $fname does not exist"
      }
      return [ create_local_file_ref $fname ]
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
    proc copy_file { outputs inputs } {
      set dst [ lindex $outputs 0 ]
      set src [ lindex $inputs 0 ]
      #  puts "dst: $dst"
      set mapped [ is_file_mapped $dst ]
      if { $mapped } {
        # is mapped, so need to make sure that file is at mapped path
        set dstpath [ get_file_path $dst ]
        set srcpath [ get_file_path $src ]
        set srcstatus [ get_file_status $src ]
        rule "$dstpath $srcpath $srcstatus" \
            [ list copy_file_body $dst $src ] \
            name "copy_file-$dst-$src" type $::turbine::WORK
      } else {
        # not mapped.  As shortcut, just make them both point to the
        # same file and update status once src file is closed
        copy_void [ get_file_status $dst ] [ get_file_status $src ]
        copy_string [ get_file_path $dst ] [ get_file_path $src ]
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
      file_read_refcount_decr $src
    }

    # return the filename of a unique temporary file
    # TODO: Do this w/o exec
    proc mktemp {} {
        set result [ exec mktemp --suffix=.turbine ]
        # puts "mktemp: $result"
        return $result
    }

    # set the filename to a string
    proc set_filename_val { file_handle filename } {
      store_string [ get_file_path $file_handle ] $filename
    }

    proc close_file { handle } {
      store_void [ get_file_status $handle ]
    }

    proc file_read_refcount_incr { handle { amount 1 } } {
      set status [ get_file_status $handle ]
      set path [ get_file_path $handle ]
      read_refcount_incr $status $amount
      read_refcount_incr $path $amount
    }

    proc file_read_refcount_decr { handle { amount 1 } } {
      file_read_refcount_incr $handle [ expr {$amount * -1} ]
    }

    proc glob { result inputs } {
        rule $inputs "glob_body $result $inputs" \
             name "glob-$result" type $::turbine::WORK
    }
    proc glob_body { result s } {
        set s_value [ retrieve_decr_string $s ]
        set r_value [ ::glob $s_value ]
        set n [ llength $r_value ]
        log "glob: $s_value tokens: $n"
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $r_value $i ]
            literal split_token string $v
            set f [ allocate_file2 "<$result>\[$i\]" $split_token ]
            close_file $f
            container_insert $result $i $f
        }
        # close container
        adlb::slot_drop $result
    }

    # Create a reference to track local file
    proc create_local_file_ref { filepath {refcount 1} } {
        return [ list $filepath $refcount ]
    }

    proc local_file_path { local_file } {
        return [ lindex $local_file 0 ]
    }

    # f: Turbine file handle
    # returns: local file ref
    proc get_file { f {decrref 0}} {
        set fname [ retrieve_string [ get_file_path $f ] $decrref ]
        if { $decrref > 0 } {
          set status [ get_file_status $f ]
          read_refcount_decr $status $decrref
        }
        # two references: global file future, local one
        return [ create_local_file_ref $fname 2 ]
    }

    proc set_file { f local_f_varname } {
       upvar 1 $local_f_varname local_f
       # Increment refcount so not cleaned up locally
       lset local_f 1 [ expr {[ lindex $local_f 1 ] + 1} ]
       store_void [ get_file_status $f ]
    }

    proc decr_local_file_refcount { varname } {
        upvar 1 $varname v
        set new_refcount [ expr {[ lindex $v 1 ] - 1} ]
        lset v 1 $new_refcount
        if { $new_refcount == 0 } {
            set path [ lindex $v 0 ]
            log "delete locally used file $path"
            file delete -force $path
        }
    }

    proc file_read { result inputs } {
	set src [ lindex $inputs 0 ]
        rule_file_helper "read_file-$src" [ list ] \
            [ list ] [ list $src ] \
            $::turbine::WORK \
            [ list file_read_body $result $src ]
    }

    proc file_read_body { result src } {
	set srcpath [ get_file_path $src ]
	set s [retrieve_string $srcpath]
        set fp [ ::open $s r ]
	set file_data [ read $fp ]
        close $fp
	store_string $result $file_data
        file_read_refcount_decr $src
    }

    proc file_read_local { local_file } {
        set fp [ ::open [ local_file_path $local_file ] r ]
	set file_data [ read $fp ]
        close $fp
        return $file_data
    }

    proc file_write { outputs inputs } {
	set dst [ lindex $outputs 0 ]
	set s_value [ retrieve_string $inputs ]
	rule_file_helper "write_file" "$inputs" \
            [ list $dst ] [ list ] \
	    $::turbine::WORK \
            [ list file_write_body $outputs $s_value ]
    }

    proc file_write_body { outputs str } {
	set dst [ lindex $outputs 0 ]
	set dstpath [ get_file_path $dst ]
	set d [ retrieve_decr_string $dstpath ]
	set fp [ ::open $d w+ ]
        puts $fp $str
	close $fp
	store_void [ get_file_status $dst ]
    }

    # local_file: local file object
    # data: data to write to file
    # TODO: calling convention not figured out yet
    proc file_write_local { local_file data } {
	set fp [ ::open [ local_file_path $local_file ] w+ ]
        puts $fp $str
	close $fp
    }

    proc blob_read { result input } {
	set src [ lindex $input 0 ]
        rule_file_helper "blob_read-$result-$src" [ list ] \
            [ list ] [ list $src ] \
            $::turbine::WORK \
            [ list blob_read_body $result $src ]
    }
    proc blob_read_body { result input } {
	set input_path [ get_file_path $input ]
	set input_name [ retrieve_string $input_path ]
        set blob [ new_turbine_blob ]
        log "blob_read: $input_name"
        blobutils_read $input_name $blob
        set ptr [ blobutils_cast_to_int \
                      [ turbine_blob_pointer_get $blob ] ]
        set length [ turbine_blob_length_get  $blob ]
        log "blob_read: length: $length"
	store_blob $result [ list $ptr $length ]
        blobutils_destroy $blob
        file_read_refcount_decr $input
    }

    proc file_lines { result input } {
        	set src [ lindex $input 0 ]
        rule_file_helper "file_lines-$result-$src" [ list ] \
            [ list ] [ list $src ] \
            $::turbine::WORK \
            [ list file_lines_body $result $src ]
    }
    proc file_lines_body { result input } {
	set input_path [ get_file_path $input ]
	set input_name [ retrieve_string $input_path ]
        set fp [ ::open $input_name r ]
        set line_number 0

        while { [ gets $fp line ] >= 0 } {
            regsub "#.*" $line "" line
            set line [ string trim $line ]
            if { [ string length $line ] > 0 } {
                literal td string $line
                container_insert $result $line_number $td 0
            }
            incr line_number
        }
        close $fp
        adlb::slot_drop $result
    }
}
