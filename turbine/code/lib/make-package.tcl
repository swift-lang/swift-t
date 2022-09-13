# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License

# MAKE-PACKAGE.TCL
# Generates the Turbine Tcl package

set turbine_version $env(TURBINE_VERSION)
set use_mpe         $env(USE_MPE)
set use_mac         $env(USE_MAC)

if { [ string equal $use_mac "yes" ] } {
    set libtclturbine libtclturbine.dylib
} else {
    set libtclturbine libtclturbine.so
}

set metadata [ list -name turbine -version $turbine_version ]

# List of Turbine shared objects and Tcl libraries
# Must be kept in sync with list in lib/module.mk.in
set items [ eval list -load $libtclturbine \
                -source turbine.tcl    \
                -source worker.tcl     \
                -source data.tcl       \
                -source assert.tcl     \
                -source logical.tcl    \
                -source arith.tcl      \
                -source container.tcl  \
                -source functions.tcl  \
                -source files.tcl      \
                -source app.tcl        \
                -source mpe.tcl        \
                -source rand.tcl       \
                -source reductions.tcl \
                -source stats.tcl      \
                -source io.tcl         \
                -source string.tcl     \
                -source updateable.tcl \
                -source sys.tcl        \
                -source blob.tcl       \
                -source location.tcl   \
                -source checkpoint.tcl \
                -source langs.tcl      \
                -source launch.tcl     \
                -source job.tcl        \
                -source python.tcl     \
                -source json.tcl       \
                -source gemtc_worker.tcl \
                -source helpers.tcl ]

puts [ eval ::pkg::create $metadata $items ]
