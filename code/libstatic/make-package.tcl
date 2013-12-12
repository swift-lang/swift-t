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

# Create package index for statically linked Tcl package. This is mainly
# a placeholder to satisfy user code that requires turbine, since we
# initialize the Tcl C module and evaluate Tcl source files through
# other means
puts [ list package ifneeded turbine $turbine_version [ list load {} turbine ] ]
