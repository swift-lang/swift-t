
// Swift/Turbine builtins defined here

// NYI = Not Yet Implemented at Turbine layer

// Data copy
(int o)    copy_integer(int i)    "turbine" "0.0.2" "copy_integer";
(float o)  copy_float  (float i)  "turbine" "0.0.2" "copy_float";
(string o) copy_string (string i) "turbine" "0.0.2" "copy_string";

// Arithmetic
(int o) plus_integer    (int i1, int i2) "turbine" "0.0.2" "plus_integer";
(int o) minus_integer   (int i1, int i2) "turbine" "0.0.2" "minus_integer";
(int o) multiply_integer(int i1, int i2) "turbine" "0.0.2" "multiply_integer";
(int o) divide_integer  (int i1, int i2) "turbine" "0.0.2" "divide_integer";
(int o) negate_integer  (int i)          "turbine" "0.0.2" "negate_integer";

(int o) and (int i1, int i2) "turbine" "0.0.2" "and";
(int o) or  (int i1, int i2) "turbine" "0.0.2" "or";

// NYI (int o) xor (int i1, int i2) "turbine" "0.0.2" "xor";
(int o) not (int i)          "turbine" "0.0.2" "not";

(float o) plus_float    (float i1, float i2) "turbine" "0.0.2" "plus_float";
(float o) minus_float   (float i1, float i2) "turbine" "0.0.2" "minus_float";
(float o) multiply_float(float i1, float i2) "turbine" "0.0.2" "multiply_float";
(float o) divide_float  (float i1, float i2) "turbine" "0.0.2" "divide_float";
(float o) negate_float  (float i)            "turbine" "0.0.2" "negate_float";

// Relational

(int o) eq_integer (int i1, int i2) "turbine" "0.0.2" "eq_integer";
(int o) neq_integer(int i1, int i2) "turbine" "0.0.2" "neq_integer";
(int o) lt_integer (int i1, int i2) "turbine" "0.0.2" "lt_integer";
(int o) lte_integer(int i1, int i2) "turbine" "0.0.2" "lte_integer";
(int o) gt_integer (int i1, int i2) "turbine" "0.0.2" "gt_integer";
(int o) gte_integer(int i1, int i2) "turbine" "0.0.2" "gte_integer";

(int o) eq_float (float i1, float i2) "turbine" "0.0.2" "eq_float";
(int o) neq_float(float i1, float i2) "turbine" "0.0.2" "neq_float";
(int o) lt_float (float i1, float i2) "turbine" "0.0.2" "lt_float";
(int o) lte_float(float i1, float i2) "turbine" "0.0.2" "lte_float";
(int o) gt_float (float i1, float i2) "turbine" "0.0.2" "gt_float";
(int o) gte_float(float i1, float i2) "turbine" "0.0.2" "gte_float";

(int o) eq_string (string i1, string i2) "turbine" "0.0.2" "eq_string";
(int o) neq_string(string i1, string i2) "turbine" "0.0.2" "neq_string";
// String lexical sorting
// NYI (string o) lt_string (string i1, string i2) "turbine" "0.0.2" "lt_string";
// NYI (string o) lte_string(string i1, string i2) "turbine" "0.0.2" "lte_string";
// NYI (string o) gt_string (string i1, string i2) "turbine" "0.0.2" "gt_string";
// NYI (string o) gte_string(string i1, string i2) "turbine" "0.0.2" "gte_string";

// String operations
(string o) strcat(string i1, string i2) "turbine" "0.0.2" "strcat";
(string o) substring(string s, int start, int length) "turbine" "0.0.2" "substring";

// Type conversion
(string o) fromint(int i)  "turbine" "0.0.2" "fromint";
(int o)    toint(string i) "turbine" "0.0.2" "toint";

// I/O
() trace (...) "turbine" "0.0.2" "trace";


