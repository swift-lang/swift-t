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

# Simple diamond test case but use ADLB

package require turbine 0.0.1

proc rules { } {
    turbine::c::file 0 /dev/null
    turbine::c::file 1 A.txt
    turbine::c::file 2 B.txt
    turbine::c::file 3 C.txt
    turbine::c::file 4 D.txt

    turbine::c::rule 1 A {     } { 1 } { touch test/data/A.txt }
    turbine::c::rule 2 B { 1   } { 2 } { touch test/data/B.txt }
    turbine::c::rule 3 C { 1   } { 3 } { touch test/data/C.txt }
    turbine::c::rule 4 D { 2 3 } { 4 } { touch test/data/D.txt }
}

namespace import turbine::adlb::*
init
start rules
finalize

puts OK
