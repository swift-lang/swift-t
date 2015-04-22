set files 8
set numbers 10
for { set i 0 } { $i < $files } { incr i } {
  set fd($i) [ open "data-$i.txt" "w" ]
}
for { set j 0 } { $j < $numbers } { incr j } {
  for { set i 0 } { $i < $files } { incr i } {
    set v [ expr 10*$j + int(rand()*10) ]
    puts $fd($i) $v
  }
}
for { set i 0 } { $i < $files } { incr i } {
  close $fd($i)
}
