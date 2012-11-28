#ifndef BLOB_SWIFT
#define BLOB_SWIFT

@pure
(int o) blob_size(blob b) "turbine" "0.0.6" "blob_size_async"
   [ "set <<o>> [ turbine::blob_size <<b>> ]" ] ;

@pure
(blob o)   blob_from_string(string s) "turbine" "0.0.2" "blob_from_string"
 [ "set <<o>> [ adlb::blob_from_string <<s>> ]" ];
@pure
(string o) string_from_blob(blob b) "turbine" "0.0.2" "string_from_blob";
@pure
(blob o) blob_from_floats(float f[]) "turbine" "0.0.2" "blob_from_floats";
@pure
(float f[]) floats_from_blob(blob b) "turbine" "0.0.2" "floats_from_blob";

#endif // BLOB_SWIFT
