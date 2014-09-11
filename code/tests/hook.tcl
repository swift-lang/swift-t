
set comm [ turbine::c::leader_comm ]

puts HOWDY

set x [ turbine::c::bcast $comm hello ]
puts "x: $x"

turbine::c::copy_to $comm tests/mpi-io.data /tmp/wozniak
