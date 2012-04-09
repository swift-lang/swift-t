
# Generate the Turbine Tcl package

set turbine_version $env(TURBINE_VERSION)
set use_mpe         $env(USE_MPE)

set load_mpe ""
# Determine if we compiled MPE
if { $use_mpe == 1 } {
    set load_mpe "-load libtclmpe.so"
}

set metadata [ list -name turbine -version $turbine_version ]

set items [ eval list -load libtcladlb.so   \
                -load libtclturbine.so \
                $load_mpe              \
                -source turbine.tcl    \
                -source engine.tcl     \
                -source data.tcl       \
                -source functions.tcl  \
                -source assert.tcl     \
                -source logical.tcl    \
                -source string.tcl     \
                -source arith.tcl      \
                -source container.tcl  \
                -source rand.tcl       \
                -source stdio.tcl      \
                -source updateable.tcl \
                -source unistd.tcl     \
                -source helpers.tcl ]

# List of Turbine shared objects and Tcl libraries
# Must be kept in sync with list in lib/module.mk.in
puts [ eval ::pkg::create $metadata $items ]
