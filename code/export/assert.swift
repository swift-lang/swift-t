
// Assertions

#ifndef ASSERT_SWIFT
#define ASSERT_SWIFT

@assertion @builtin_op=ASSERT
assert(boolean condition, string msg) "turbine" "0.0.2" "assert";
@assertion @builtin_op=ASSERT_EQ
assertEqual(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertEqual";
@assertion
assertLT(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLT";
@assertion
assertLTE(string|int|float|boolean v1, string|int|float|boolean v2,
               string msg) "turbine" "0.0.2" "assertLTE";

#endif
