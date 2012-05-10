
// Assertions

#ifndef ASSERT_SWIFT
#define ASSERT_SWIFT

assert(boolean condition, string msg) "turbine" "0.0.2" "assert";
assertEqual(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertEqual";
assertLT(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLT";
assertLTE(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLTE";

#endif
