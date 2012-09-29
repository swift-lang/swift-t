
// Mathematical functions

#ifndef MATH_SWIFT
#define MATH_SWIFT

@pure @builtin_op=FLOOR
(int o) floor           (float i) "turbine"  "0.0.2" "floor";
@pure @builtin_op=CEIL
(int o) ceil            (float i) "turbine"  "0.0.2" "ceil";
@pure @builtin_op=ROUND
(int o) round           (float i) "turbine"  "0.0.2" "round";
@pure @builtin_op=LOG
(float o) log           (float i) "turbine"  "0.0.2" "log_e";
@pure @builtin_op=EXP
(float o) exp           (float i) "turbine"  "0.0.2" "exp";
@pure @builtin_op=SQRT
(float o) sqrt          (float i) "turbine"  "0.0.2" "sqrt";
@pure @builtin_op=IS_NAN
(boolean o) is_nan      (float i) "turbine"  "0.0.2" "is_nan";
@pure @builtin_op=ABS_INT
(int o)   abs_integer (int i)   "turbine"  "0.0.2" "abs_integer";
@pure @builtin_op=ABS_FLOAT
(float o) abs_float   (float i) "turbine"  "0.0.2" "abs_float";

#endif
