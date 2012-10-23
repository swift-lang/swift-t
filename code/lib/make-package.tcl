
# Generate the Turbine Tcl package

set turbine_version $env(TURBINE_VERSION)
set use_mpe         $env(USE_MPE)
set use_mac         $env(USE_MAC)

if { [ string equal $use_mac "yes" ] } {
    set libtclturbine libtclturbine.dylib
} else {
    set libtclturbine libtclturbine.so
}

set metadata [ list -name turbine -version $turbine_version ]

set items [ eval list -load $libtclturbine \
                -source turbine.tcl    \
                -source engine.tcl     \
                -source data.tcl       \
                -source assert.tcl     \
                -source logical.tcl    \
                -source arith.tcl      \
                -source container.tcl  \
                -source functions.tcl  \
                -source files.tcl      \
                -source mpe.tcl        \
                -source rand.tcl       \
                -source stats.tcl      \
                -source io.tcl      \
                -source string.tcl     \
                -source updateable.tcl \
                -source sys.tcl     \
                -source blob.tcl    \
                -source helpers.tcl ]

# List of Turbine shared objects and Tcl libraries
# Must be kept in sync with list in lib/module.mk.in
puts [ eval ::pkg::create $metadata $items ]
