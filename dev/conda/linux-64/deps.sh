
# linux-64 DEPS SH

export USE_NCURSES=1
# For some reason zlib is not found on linux-64, Python 3.8
#     as of 2024-12-11:
# Needed for all linux-64 as of 2024-12-17
export USE_ZLIB=1

# Declare Tcl/Tk explicitly: 2026-10-09
# libtclturbine.so links libtcl8.6.so, but tcl was only pulled in
# transitively and was not a declared dependency (conda-build warned the
# DSO was "not found in packages").  The tk package provides tclsh8.6 and
# libtcl8.6.so; with it in the host: section they land in $PREFIX (see
# settings.sed TCLSH) and Conda relocates them.
export USE_TK=1
export SPEC_TK='tk'

# Pin a modern MPICH: 2026-10-09
# The unpinned default (mpich-mpicc) let the solver pull an ancient
# mpich 3.2.1 whose mpi.h predates MPICH_API_PUBLIC, so Turbine's
# maint/mpi_identification.c could not detect MPICH and set
# MPI_IMPL=UNKNOWN (build failed: #error "Must specify MPI_IMPL!").
# 4.x/5.x define MPICH_API_PUBLIC.
export SPEC_MPICH='mpich-mpicc=4.3.2'
