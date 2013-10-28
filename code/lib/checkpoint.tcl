
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

# Functions for checkpointing
namespace eval turbine {

  # Initialize checkpointing, getting settings from environment
  proc xpt_init { } {
    # TODO: get from env vars 
    set xpt_filename "tmp.xpt"
    set flush_mode periodic_flush
    
    # Default to 1mb
    set max_index_val [ expr 1024 * 1024 ]
    adlb::xpt_init $xpt_filename $flush_mode $max_index_val
  }

  proc xpt_finalize { } {
    adlb::xpt_finalize 
  }
}
