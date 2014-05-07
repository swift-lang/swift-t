/*
 * Copyright 2013 University of Chicago and Argonne National Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

#ifndef BLOB_SWIFT
#define BLOB_SWIFT

@pure
(int o) blob_size(blob b) "turbine" "0.0.6" "blob_size_async"
   [ "set <<o>> [ turbine::blob_size <<b>> ]" ] ;

@pure
(blob o)   blob_null() "turbine" "0.0.2" "blob_null"
 [ "set <<o>> [ turbine::blob_null ]" ];
@pure
(blob o)   blob_from_string(string s) "turbine" "0.0.2" "blob_from_string"
 [ "set <<o>> [ adlb::blob_from_string <<s>> ]" ];
@pure
(string o) string_from_blob(blob b) "turbine" "0.0.2" "string_from_blob"
 [ "set <<o>> [ adlb::blob_to_string <<b>> ]" ];
@pure
(blob o) blob_from_floats(float f[]) "turbine" "0.0.2"
  [ "set <<o>> [ turbine::blob_from_floats_impl <<f>> ]" ];
@pure
(blob o) blob_from_ints(int i[]) "turbine" "0.0.2"
  [ "set <<o>> [ turbine::blob_from_ints_impl <<i>> ] " ];
@pure
(float f[]) floats_from_blob(blob b) "turbine" "0.0.2"
  [ "set <<f>> [ turbine::floats_from_blob_impl <<b>> ] " ];

// TODO: inline version of blob_read
@pure @dispatch=WORKER
(blob o) blob_read(file f) "turbine" "0.0.2" "blob_read";
@pure @dispatch=WORKER
(file f) blob_write(blob o) "turbine" "0.0.2"
  [ "turbine::blob_write_local <<f>> <<o>>" ];
@pure
(blob o) blob_zeroes_float(int n)
"turbine" "0.2.0"
[ "set <<o>> [ turbine::blob_zeroes_float <<n>> ]" ];

turbine_run_output_blob(blob b)
"turbine" "0.4.0" "turbine_run_output_blob";

// Not Yet Implemented:
@pure
(blob o[]) blob_pack_rows_float(float A[][])
"turbine" "0.1.0" "blob_pack_rows_float";

#endif // BLOB_SWIFT
