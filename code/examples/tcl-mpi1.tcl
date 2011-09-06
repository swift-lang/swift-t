
# Does not actually use MPI except for PMI_RANK

global env
puts "$env(PWD) $env(PMI_RANK) $env(JETS_CORE) $env(JETS_SPECIFIER)"
exit
