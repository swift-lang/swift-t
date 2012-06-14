
// Swift/Turbine builtins defined here

#ifndef BUILTINS_SWIFT
#define BUILTINS_SWIFT

// NYI = Not Yet Implemented at Turbine layer

// Data copy
@pure @copy
(void o) copy_void (void i) "turbine" "0.0.2" "copy_void";
@pure @copy
(blob o) copy_blob (blob i) "turbine" "0.0.2" "copy_blob";
@pure @copy
(file o) copy_file (file i) "turbine" "0.0.2" "copy_file";
@pure
(void o) make_void () "turbine" "0.0.2" "make_void";

// Arithmetic
@pure @minmax @builtin_op=MAX_INT
(int o) max_integer     (int i1, int i2) "turbine" "0.0.2" "max_integer";
@pure @minmax @builtin_op=MIN_INT
(int o) min_integer     (int i1, int i2) "turbine" "0.0.2" "min_integer";
@pure @minmax @builtin_op=POW_INT
(float o) pow_integer     (int i1, int i2) "turbine" "0.0.2" "pow_integer";

// This is used by the string+ concatenation operator
@pure @builtin_op=STRCAT
(string o) strcat(string... args) "turbine" "0.0.2" "strcat";

@pure @commutative @builtin_op=XOR
(boolean o) xor (boolean i1, boolean i2) "turbine" "0.0.2" "neq_integer";

@pure @minmax @builtin_op=MAX_FLOAT
(float o) max_float     (float i1, float i2) "turbine" "0.0.2" "max_float";
@pure @minmax @builtin_op=MIN_FLOAT
(float o) min_float     (float i1, float i2) "turbine" "0.0.2" "min_float";
@pure @builtin_op=POW_FLOAT
(float o) pow_float     (float i1, float i2) "turbine" "0.0.2" "pow_float";

// Type conversion
@pure @builtin_op=INTTOSTR
(string o) fromint(int i)  "turbine" "0.0.2" "fromint";
@pure @builtin_op=STRTOINT
(int o)    toint(string i) "turbine" "0.0.2" "toint";
@pure @builtin_op=FLOATTOSTR
(string o) fromfloat(float i)  "turbine" "0.0.2" "fromfloat";
@pure @builtin_op=STRTOFLOAT
(float o) tofloat(string i)  "turbine" "0.0.2" "tofloat";
@pure @builtin_op=INTTOFLOAT
(float o) itof    (int i) "turbine"  "0.0.2" "itof";
@pure
(blob o)   blob_from_string(string s) "turbine" "0.0.2" "blob_from_string";
@pure
(string o) string_from_blob(blob b) "turbine" "0.0.2" "string_from_blob";
@pure
(blob o) blob_from_floats(float f[]) "turbine" "0.0.2" "blob_from_floats";

// I/O
@builtin_op=TRACE
trace (int|float|string|boolean... args) "turbine" "0.0.2" "trace";
sleep_trace (float secs, int|float|string|boolean... args) "turbine" "0.0.2"
                                                            "sleep_trace";

// Container operations
@pure
(int res[]) range(int start, int end) "turbine" "0.0.2" "range";
@pure
(int res[]) rangestep(int start, int end, int step) "turbine" "0.0.2" "rangestep";

// Updateable variables
(updateable_float o) init_updateable(float i) "turbine" "0.0.2" "init_updateable";

// Information about cluster
// pure because it won't change during program execution
@pure @builtin_op=N_WORKERS
(int n) adlb_servers() "turbine" "0.0.2" "adlb_servers_future"
    [ "set <<n>> turbine::adlb_servers" ];
@pure @builtin_op=N_ENGINES
(int n) turbine_engines() "turbine" "0.0.2" "turbine_engines_future";
@pure @builtin_op=N_ADLB_SERVERS
(int n) turbine_workers() "turbine" "0.0.2" "turbine_workers_future";

// Basic file ops
@pure
(string n) filename(file x) "turbine" "0.0.2" "filename2";
@pure
(file f) input_file(string filename) "turbine" "0.0.2" "input_file";

#endif
