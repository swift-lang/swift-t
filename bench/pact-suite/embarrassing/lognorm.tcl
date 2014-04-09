package provide lognorm 0.0

# Log normal distribution implementation
namespace eval lognorm {
  namespace export sample samples norm_samples

  proc sample { mu sigma } {
    samples $mu $sigma result tmp
    return $result
  }

  # Produce two random samples from log normal dist
  proc samples { mu sigma s1_name s2_name } {
    upvar 1 $s1_name s1
    upvar 1 $s2_name s2
    
    norm_samples $mu $sigma norm_s1 norm_s2

    set s1 [ expr {exp($norm_s1)} ]
    set s2 [ expr {exp($norm_s2)} ]
  }

  # Produce two random samples from normal dist
  proc norm_samples { mu sigma s1_name s2_name } { 
    upvar 1 $s1_name s1
    upvar 1 $s2_name s2

    while { 1 } {
      # Marsaglia polar method

      # Scale to (-1,1)
      set r1 [ expr {2 * rand() - 1} ]
      set r2 [ expr {2 * rand() - 1} ]
      set s [ expr {$r1 * $r1 + $r2 * $r2} ]
      if { [ expr { $s < 1 } ] } {
        set s1 [ expr {$mu + $sigma * ($r1 * sqrt((-2 * log($s) ) / $s))} ]
        set s2 [ expr {$mu + $sigma * ($r2 * sqrt((-2 * log($s) ) / $s))} ]
        return
      }
    }
  }
}
