
proc fix-home { p } {
  return [ regsub /home $p /gpfs/mira-home ]
}

puts WAITER

puts [ exec ls /home/wozniak ]

set TIC [ lindex $argv 0 ]

puts "TLP: $env(TCLLIBPATH)"
# puts [ exec ls $env(TCLLIBPATH) ]
set auto_path [ fix-home $env(TCLLIBPATH) ]
puts "auto_path: $auto_path"

puts [ exec ls $auto_path ]

puts "TIC: $TIC"
set TIC [ fix-home $TIC ]

set delay 0
while true {
  if [ file exists $TIC ] break
  puts [ clock seconds ]
  set dir [ file dirname $TIC ]
  if [ file exists $dir ] {
    puts [ exec ls [ file dirname $TIC ] ]
  } else {
    puts "No directory: $dir"
  }
  puts ""
  after [ expr $delay * 1000 ]
  incr delay
}

source $TIC
