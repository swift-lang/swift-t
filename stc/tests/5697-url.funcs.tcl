
package provide funcs_5697 0.5

namespace eval funcs_5697 {
  package require turbine 1.0
  namespace import ::turbine::*

  proc copy_url5 { outputs inputs } {
    puts [ list Entering copy_url5 $outputs $inputs ]
    set outurl [ lindex $outputs 0 ]
    set inurl [ lindex $inputs 0 ]

    rule [ list [ get_file_status $inurl ] [ get_file_path $outurl ] ] \
         [ list funcs_5697::copy_url5_body $outurl $inurl ]
  }

  proc copy_url5_body { outurl inurl  } {
    puts [ list Entering copy_url5_body $outurl $inurl ]
    set outpath [ retrieve_string [ get_file_path $outurl ] ]
    set inpath [ retrieve_decr_string [ get_file_path $inurl ] ]

    copy_url5_impl $inpath $outpath
    turbine::close_file $outurl
  }

  proc copy_url5_impl { inpath outpath } {
    puts [ list Entering copy_url5_impl $outpath $inpath ]
    puts [ list copy_url5 $inpath $outpath ]
  }
}
