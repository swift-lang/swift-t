source lognorm.tcl
package require lognorm


foreach mu [ list -9 -8.5 -8 -7.5 -7 ] {
  foreach sigma [ list 1 ] {
    set mean [ lognorm::mean $mu $sigma ]
    set stdev [ lognorm::stdev $mu $sigma ]
    puts [ format "mu: %f sigma %f mean: %.2fms stdev: %.2fms"  \
           $mu $sigma [ expr {${mean}*1000}] [ expr {${stdev}*1000} ] ]
  }
}
