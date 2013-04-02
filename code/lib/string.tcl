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

# Turbine builtin string functions

# All have the same signature
#   f <STACK> <OUTPUT LIST> <INPUT LIST>
# where the lists are Tcl lists of TDs

namespace eval turbine {

    # User function
    # usage: strcat <result> <args>*
    proc strcat { result inputs } {
        rule $inputs "strcat_body $result $inputs" \
            name "strcat-$result" 
    }

    # usage: strcat_body <result> <args>*
    proc strcat_body { result args } {
        set output [ list ]
        foreach input $args {
            set t [ retrieve_decr_string $input ]
            lappend output $t
        }
        set total [ join $output "" ]
        store_string $result $total
    }

    # Substring of s starting at i of length n
    proc substring { result inputs  } {

        set s [ lindex $inputs 0 ]
        set i [ lindex $inputs 1 ]
        set n [ lindex $inputs 2 ]
        rule $inputs "substring_body $result $s $i $n" \
            name "substring-$s-$i-$n" 
    }

    proc substring_body { result s i n } {
        set s_value [ retrieve_decr_string  $s ]
        set i_value [ retrieve_decr_integer $i ]
        set n_value [ retrieve_decr_integer $n ]

        set result_value [ substring_impl $s_value $i_value $n_value ]
        store_string $result $result_value
    }

    proc substring_impl { s i n } {
        set last [ expr {$i + $n - 1} ]
        return [ string range $s $i $n ]
    }

    # This accepts an optional delimiter
    # (STC does not yet support optional arguments)
    proc split { args } {
        set result [ lindex $args 0 ]
        set inputs [ lreplace $args 0 0 ]

        # Unpack inputs
        set inputs [ lindex $inputs 0 ]

        show result
        show inputs

        set s [ lindex $inputs 0 ]
        if { [ llength $inputs ] == 2 } {
            set delimiter [ lindex $inputs 1 ]
            rule [ list $s $delimiter ] \
                "split_body $result $s $delimiter" \
                name "split-$result" 
        } elseif { [ llength $inputs ] == 1 } {
            # Use default delimiter: " "
            set delimiter 0
            rule $s "split_body $result $s 0" name "split-$result" 
        } else {
            error "split requires 1 or 2 arguments"
        }
    }

    # Split string s with delimiter d into result container r
    # Tcl split should handle spaces correctly:
    # http://tmml.sourceforge.net/doc/tcl/split.html
    proc split_body { result s delimiter } {
        set s_value [ retrieve_decr_string $s ]
        if { $delimiter == 0 } {
            set d_value " "
        } else {
            set d_value [ retrieve_decr_string $delimiter ]
        }
        set r_value [ ::split $s_value $d_value ]
        set n [ llength $r_value ]
        log "split: $s_value on: $d_value tokens: $n"
        for { set i 0 } { $i < $n } { incr i } {
            set v [ lindex $r_value $i ]
            literal split_token string $v
            container_insert $result $i $split_token
        }
        # close container
        adlb::slot_drop $result
    }

    proc sprintf { result inputs } {
        rule $inputs "sprintf_body $result $inputs" \
            name "sprintf-$result" 
    }
    proc sprintf_body { result args } {
        set L [ list ]
        foreach a $args {
            lappend L [ retrieve_decr $a ]
        }
        set s [ eval format $L ]
        store_string $result $s
    }

    proc find { result inputs } {
	set str         [ lindex $inputs 0 ]
	set subs        [ lindex $inputs 1 ]
	set start_index [ lindex $inputs 2 ]
	set end_index   [ lindex $inputs 3 ]
	rule $inputs \
	    "find_body $result $str $subs $start_index $end_index" \
            name "find-$result" 
    }

    proc find_body { result str subs start_index end_index } {
	set str_value  [ retrieve_decr_string $str ]
	set subs_value [ retrieve_decr_string $subs ]
	set start_index_value [ retrieve_decr_integer $start_index ]
	set end_index_value  [ retrieve_decr_integer $end_index ]

	set result_value [ find_impl $str_value $subs_value \
			       $start_index_value $end_index_value ]

	store_integer $result $result_value
    }

    # Find the index of the first occurence of the substring in the
    # given string. Returns -1 if there was no valid match
    # By default start_index is 0 and end_index is -1
    proc find_impl {str subs {start_index 0} {end_index -1} } {
	if { $end_index == -1 } {
	    set end_index [string length $str]
	}
	set ret [string first $subs $str $start_index]
	set len [string length $subs]
	set subs_end [expr {$ret + $len} ]
	if { $subs_end <= $end_index } {
	    return $ret
	} else {
	    return -1
	}
    }

    proc count { result inputs } {
	set str         [ lindex $inputs 0 ]
	set subs        [ lindex $inputs 1 ]
	set start_index [ lindex $inputs 2 ]
	set end_index   [ lindex $inputs 3 ]
	rule $inputs \
            "count_body $result $str $subs $start_index $end_index" \
            name "count-$str-$subs-$start_index-$end_index" 
    }

    proc count_body { result str subs start_index end_index } {
	set str_value  [ retrieve_decr_string $str ]
	set subs_value [ retrieve_decr_string $subs ]
	set start_index_value [ retrieve_decr_integer $start_index ]
	set end_index_value  [ retrieve_decr_integer $end_index ]

	set result_value [ count_impl $str_value $subs_value \
			  $start_index_value $end_index_value ]
        # puts "count_impl $str_value $subs_value $start_index_value $end_index_value = $result_value"

	store_integer $result $result_value
    }

    # Find the number of occurences of the substring in the given
    # string. Returns 0 if no matches are present. By default
    # start_index is set to 0 and end_index is set to -1 which
    # implies the end of the string.
    proc count_impl {str subs {start_index 0} {end_index -1} } {
	if { $end_index == -1 } {
	    set end_index [string length $str]
	}
        set subs_len [ string length $subs ]
	set found 0
	for {set index $start_index} { $index <= $end_index } {incr index} {
	    set r [ find_impl $str $subs $index $end_index ];
	    if { $r == -1} {
              return $found
            } else {
              # Move to end of occurrence to avoid counting overlaps
	      set index [ expr {$r  + $subs_len - 1} ]
            }
	    incr found
	}
    }

    proc isint { result inputs } {
	set str [ lindex $inputs 0 ]
	rule $inputs "isint_body $result $str" \
            name "isint-$result" 
    }

    proc isint_body { result str } {
	set str_value [ retrieve_decr_string $str ]
	set result_value [ isint_impl $str_value ]
	store_integer $result $result_value
    }

    # Returns 1 if string is an integer, 0 otherwise
    proc isint_impl { str } {
	return [ string is wideinteger -strict $str ];
    }

    proc replace { result inputs } {
	set str         [ lindex $inputs 0 ]
	set substring  [ lindex $inputs 1 ]
	set rep_string  [ lindex $inputs 2 ]
	set start_index [ lindex $inputs 3 ]
	rule $inputs \
            [ list replace_body $result $str \
                  $substring $rep_string $start_index ] \
            name "replace-$str-$substring-$rep_string-$start_index" 
    }

    proc replace_body { result str substring rep_string start_index } {
	set str_value         [ retrieve_decr_string $str ]
	set substring_value   [ retrieve_decr_string $substring ]
	set rep_string_value  [ retrieve_decr_string $rep_string ]
	set start_index_value [ retrieve_decr_integer $start_index ]

	set result_value [ replace_impl $str_value $substring_value \
			       $rep_string_value $start_index_value ]
	store_string $result $result_value
    }

    # Replaces the first occurrence of the substring with the replacement
    # string. If no matches were possible returns the original string.
    proc replace_impl {str substring rep_string {start_index 0} } {
	set start [find_impl $str $substring $start_index]
	#If the substring is absent the string is NOT modified
	if { $start == -1 } { return $str };
	set end [expr {$start + [string length $substring]} ]
	set part1 [string range $str 0  [ expr {$start-1} ] ]
	set part2  [string range $str $end end]
        return "$part1$rep_string$part2"
    }

    proc replace_all { result inputs } {
	set str         [ lindex $inputs 0 ]
	set substring  [ lindex $inputs 1 ]
	set rep_string  [ lindex $inputs 2 ]
	set start_index  [ lindex $inputs 3 ]
	rule $inputs \
            [ list replace_all_body $result $str \
                  $substring $rep_string $start_index ] \
            name "replace_all-$str-$substring-$rep_string" 
    }

    proc replace_all_body { result str substring rep_string start_index } {
	set str_value         [ retrieve_decr_string $str ]
	set substring_value   [ retrieve_decr_string $substring ]
	set rep_string_value  [ retrieve_decr_string $rep_string ]
	set start_index_value [ retrieve_decr_integer $start_index ]

	set result_value [ replace_all_impl $str_value \
                 $substring_value $rep_string_value $start_index_value ]
	store_string $result $result_value
    }

    # Replaces all occurrences of the substring with the replacement
    # string. Returns the original string if no replacement was possible
    proc replace_all_impl {str substring rep_string start_index } {
	set end_index [string length $str ]
        set substring_len [ string length $substring ]
        set result [ string range $str 0 [ expr {$start_index - 1} ] ]

	for {set index $start_index} { $index <= $end_index } {incr index} {
	    set r [ find_impl $str $substring $index $end_index ];
	    if { $r == -1 } {
              append result [ string range $str $index $end_index ]
              return $result
            }
            # append skipped part
            append result [ string range $str $index [ expr {$r - 1} ] ]
            # append the replacement and skip over rest of substring
            append result $rep_string
	    set index [ expr {$r + $substring_len - 1} ]
	}
        return $result
    }

}
