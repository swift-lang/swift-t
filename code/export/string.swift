
// STRING.SWIFT

#ifndef STRING_SWIFT
#define STRING_SWIFT

@pure @builtin_op=SUBSTRING
(string o) substring(string s, int start, int length)
"turbine" "0.0.2" "substring";

@pure
(string t[]) split(string s, string delimiter)
"turbine" "0.0.2" "split";

@builtin_op=SPRINTF
(string o) sprintf(string fmt, int|float|string|boolean... args)
"turbine" "0.0.2" "sprintf";

#endif
