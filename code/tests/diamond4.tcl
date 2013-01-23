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

package require turbine 0.0.1

# Use turbine lang functionality

turbine::program {

    function touch { d } {
        # Like Swift @ syntax
        shell touch [@ d]
    }

    main {
        declare file f1 "test/data/A.txt"
        declare file f2 "test/data/B.txt"
        declare file f3 "test/data/C.txt"
        declare file f4 "test/data/D.txt"

        when ready {    }    { touch f1 } creates { f1 }
        when ready { f1 }    { touch f2 } creates { f2 }
        when ready { f1 }    { touch f3 } creates { f3 }
        when ready { f2 f3 } { touch f4 } creates { f4 }
    }
}
