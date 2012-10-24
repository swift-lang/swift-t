
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

@pure
(int o) find(string s, string substring, int start_index, int end_index)
"turbine" "0.0.1" "find";

@pure
(int o) count(string s, string substring, int start_index, int end_index)
"turbine" "0.0.1" "find";

@pure
(boolean o) isnum(string s)
"turbine" "0.0.1" "isnum";

@pure
(string o) replace (string s, string substring, string rep_string, int start_index)
"turbine" "0.0.1" "replace";

@pure
(string o) replace_all (string s, string substring, string rep_string)
"turbine" "0.0.1" "replace_all";

#endif
