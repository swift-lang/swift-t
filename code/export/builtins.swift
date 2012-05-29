
// Swift/Turbine builtins defined here

#ifndef BUILTINS_SWIFT
#define BUILTINS_SWIFT

// NYI = Not Yet Implemented at Turbine layer

// Data copy
(int o)    copy_integer(int i)    "turbine" "0.0.2" "copy_integer";
(float o)  copy_float  (float i)  "turbine" "0.0.2" "copy_float";
(string o) copy_string (string i) "turbine" "0.0.2" "copy_string";
(boolean o) copy_boolean (boolean i) "turbine" "0.0.2" "copy_integer";
(void o) copy_void (void i) "turbine" "0.0.2" "copy_void";
(blob o) copy_blob (blob i) "turbine" "0.0.2" "copy_blob";
(file o) copy_file (file i) "turbine" "0.0.2" "copy_file";
(void o) make_void () "turbine" "0.0.2" "make_void";

// Arithmetic
(int o) plus_integer    (int i1, int i2) "turbine" "0.0.2" "plus_integer";
(int o) minus_integer   (int i1, int i2) "turbine" "0.0.2" "minus_integer";
(int o) multiply_integer(int i1, int i2) "turbine" "0.0.2" "multiply_integer";
(int o) divide_integer  (int i1, int i2) "turbine" "0.0.2" "divide_integer";
(int o) mod_integer     (int i1, int i2) "turbine" "0.0.2" "mod_integer";
(int o) negate_integer  (int i)          "turbine" "0.0.2" "negate_integer";
(int o) max_integer     (int i1, int i2) "turbine" "0.0.2" "max_integer";
(int o) min_integer     (int i1, int i2) "turbine" "0.0.2" "min_integer";
(float o) pow_integer     (int i1, int i2) "turbine" "0.0.2" "pow_integer";

// This is used by the string+ concatenation operator
(string o) strcat(string... args) "turbine" "0.0.2" "strcat";

(boolean o) and (boolean i1, boolean i2) "turbine" "0.0.2" "and";
(boolean o) or  (boolean i1, boolean i2) "turbine" "0.0.2" "or";

(boolean o) xor (boolean i1, boolean i2) "turbine" "0.0.2" "neq_integer";
(boolean o) not (boolean i)          "turbine" "0.0.2" "not";

(float o) plus_float    (float i1, float i2) "turbine" "0.0.2" "plus_float";
(float o) minus_float   (float i1, float i2) "turbine" "0.0.2" "minus_float";
(float o) multiply_float(float i1, float i2) "turbine" "0.0.2" "multiply_float";
(float o) divide_float  (float i1, float i2) "turbine" "0.0.2" "divide_float";
(float o) negate_float  (float i)            "turbine" "0.0.2" "negate_float";
(float o) max_float     (float i1, float i2) "turbine" "0.0.2" "max_float";
(float o) min_float     (float i1, float i2) "turbine" "0.0.2" "min_float";
(float o) pow_float     (float i1, float i2) "turbine" "0.0.2" "pow_float";

// Relational
(boolean o) eq_integer (int i1, int i2) "turbine" "0.0.2" "eq_integer";
(boolean o) neq_integer(int i1, int i2) "turbine" "0.0.2" "neq_integer";
(boolean o) lt_integer (int i1, int i2) "turbine" "0.0.2" "lt_integer";
(boolean o) lte_integer(int i1, int i2) "turbine" "0.0.2" "lte_integer";
(boolean o) gt_integer (int i1, int i2) "turbine" "0.0.2" "gt_integer";
(boolean o) gte_integer(int i1, int i2) "turbine" "0.0.2" "gte_integer";

(boolean o) eq_float (float i1, float i2) "turbine" "0.0.2" "eq_float";
(boolean o) neq_float(float i1, float i2) "turbine" "0.0.2" "neq_float";
(boolean o) lt_float (float i1, float i2) "turbine" "0.0.2" "lt_float";
(boolean o) lte_float(float i1, float i2) "turbine" "0.0.2" "lte_float";
(boolean o) gt_float (float i1, float i2) "turbine" "0.0.2" "gt_float";
(boolean o) gte_float(float i1, float i2) "turbine" "0.0.2" "gte_float";

(boolean o) eq_string (string i1, string i2) "turbine" "0.0.2" "eq_string";
(boolean o) neq_string(string i1, string i2) "turbine" "0.0.2" "neq_string";
// String lexical sorting
// NYI (boolean o) lt_string (string i1, string i2) "turbine" "0.0.2" "lt_string";
// NYI (boolean o) lte_string(string i1, string i2) "turbine" "0.0.2" "lte_string";
// NYI (boolean o) gt_string (string i1, string i2) "turbine" "0.0.2" "gt_string";
// NYI (boolean o) gte_string(string i1, string i2) "turbine" "0.0.2" "gte_string";

(boolean o) eq_boolean (boolean i1, boolean i2) "turbine" "0.0.2" "eq_integer";
(boolean o) neq_boolean(boolean i1, boolean i2) "turbine" "0.0.2" "neq_integer";

// Type conversion
(string o) fromint(int i)  "turbine" "0.0.2" "fromint";
(int o)    toint(string i) "turbine" "0.0.2" "toint";
(string o) fromfloat(float i)  "turbine" "0.0.2" "fromfloat";
(float o) tofloat(string i)  "turbine" "0.0.2" "tofloat";
(float o) itof    (int i) "turbine"  "0.0.2" "itof";
(blob o)   blob_from_string(string s) "turbine" "0.0.2" "blob_from_string";
(string o) string_from_blob(blob b) "turbine" "0.0.2" "string_from_blob";
(blob o) blob_from_floats(float f[]) "turbine" "0.0.2" "blob_from_floats";

// I/O
trace (int|float|string|boolean... args) "turbine" "0.0.2" "trace";
sleep_trace (float secs, int|float|string|boolean... args) "turbine" "0.0.2"
                                                            "sleep_trace";

// Container operations
(int res[]) range(int start, int end) "turbine" "0.0.2" "range";
(int res[]) rangestep(int start, int end, int step) "turbine" "0.0.2" "rangestep";

// Updateable variables
(updateable_float o) init_updateable(float i) "turbine" "0.0.2" "init_updateable";

// Information about cluster
(int n) adlb_servers() "turbine" "0.0.2" "adlb_servers_future";
(int n) turbine_engines() "turbine" "0.0.2" "turbine_engines_future";
(int n) turbine_workers() "turbine" "0.0.2" "turbine_workers_future";

// Basic file ops
(string n) filename(file x) "turbine" "0.0.2" "filename2";
(file f) input_file(string filename) "turbine" "0.0.2" "input_file";

#endif
