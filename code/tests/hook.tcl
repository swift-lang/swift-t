
puts [ adlb::rank [ turbine::c::leader_comm ] ]
puts [ adlb::rank ]
turbine::c::copy_to [turbine::c::leader_comm] tests/mpi-io.data /tmp/wozniak
