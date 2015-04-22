
package provide sudoku 0.0

# Dummy functions that emulate sudoku app
namespace eval sudoku {
    package require turbine 0.3.0
    namespace import ::turbine::*

    # Total squares on board
    proc board_size { } {
      return [ expr 9 * 9 ]
    }
    
    proc parse_board { fname } {
        # initial value for board
        # to emulate the actual sudoku solver, we assume that the
        # secret solution is a string of all 0's with the length the
        # size of the board
        return [ adlb::blob_from_string "0" ]
    }

    # Check if string is all zeroes
    proc all_zeros { str } {
        set n [ string length $str ]
        for { set i 0 } { $i < $n } { incr i } {
            set c [ string index $str $i ]
            if { $c != 0 } {
                return 0
            }
        }
        # match!
        return 1
    }

    proc sudoku_step { solved board breadthfirst quota } {

        if { $solved > 0.0 } {
            turbine::log "solved elsewhere!"
            return
        }

        set board_str [ ::adlb::blob_to_string $board ]
        puts "Working on board: \"${board_str}\""

        # build list of candidates based on input
        # Want to make sure that we always end up with a candidate
        # Doesn't matter if we have multiple viable solutions
        set boardl [ list ]
        # Work out max and min number of squares to fill
        set maxfill [ expr [ board_size ] - [ string length $board_str ] ]
        puts "maxfill=${maxfill} board=${board_str}"

        # Number of bad solutions to generate
        # NOTE: test runtime is sensitive to this.
        #       > 2 leads to exponential growth in solutions
        #       < 2 leads to exponential decay
        set badsols [ expr round(rand() * 10) ]
        puts stderr "Badsols: ${badsols}"
        for { set i 0 } { $i < $badsols } { incr i } {
            # random digit from 0 to 9
            set fillval [ expr round(rand() * 9) ] 
            # random amount of characters to fill in
            set fillchars [ expr max(1, min($maxfill, round(rand() * 10))) ]
            set badsol "$board_str[ string repeat $fillval $fillchars ]"
            puts stderr "New solution: $badsol"
            lappend boardl $badsol
        }

        if [ all_zeros $board_str ] {
            puts "Was all zeroes"
            # Possible solution, always make sure we add a zero to get
            # correct solution
            set fillchars [ expr max(1, min($maxfill, round(rand() * 10))) ]
            set goodsol "$board_str[ string repeat 0 $fillchars ]"
            puts stderr "Partial correct solution: $goodsol"
            lappend boardl $goodsol
        }

        set n [ llength $boardl ]
        puts stderr "${n} new boards at [ clock clicks -milliseconds ]"
        
        set output [ dict create ]

        for { set i 0 } { $i < $n } { incr i } {
            set board [ lindex $boardl $i ]
            set filled [ string length $board ]
            puts stderr "Next board: \"${board}\" filled ${filled}"

            # Set the blob
            set board_blob [ adlb::blob_from_string $board ]
            # TODO: free blob?

            set struct [ dict create "board" $board_blob "filledSquares" $filled ]
            dict append output $i $struct
        }

        turbine::log "sudoku_step_body done => $output"
        return $output
    }

    proc print_board_tcl { board } {
        # String was stored into blob, so just print string repr
        puts [ ::adlb::blob_to_string $board ]
    }
}
