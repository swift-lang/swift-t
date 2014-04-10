source lib/lognorm.tcl
package require lognorm


foreach mu [ list -6.905 -7.6 -7.61 -7.62 ] {
  foreach sigma [ list 1 ] {
    set mean [ lognorm::mean $mu $sigma ]
    set stdev [ lognorm::stdev $mu $sigma ]
    puts [ format "mu: %f sigma %f mean: %.4fms stdev: %.2fms"  \
           $mu $sigma [ expr {${mean}*1000}] [ expr {${stdev}*1000} ] ]
  }
}
