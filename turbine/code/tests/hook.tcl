
set comm [ adlb::comm_leaders ]

puts HOWDY

set x "hello"
turbine::c::bcast $comm 0 x
puts "x: $x"

turbine::c::copy_to $comm tests/mpi-io.data /tmp/wozniak
