
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

/* find: returns first index of substring in string, or -1 if not found */
@pure
(int o) find(string s, string substring, int start_index, int end_index)
"turbine" "0.0.1" "find";

/* count: number of non-overlapping occurences of substring in string */
@pure
(int o) count(string s, string substring, int start_index, int end_index)
"turbine" "0.0.1" "count";

/* returns true if string is a decimal integer within range of Swift's
  int type */
@pure
(boolean o) isint(string s)
"turbine" "0.0.1" "isint";

/* replace first occurence of substring with rep_string */
@pure
(string o) replace (string s, string substring, string rep_string, int start_index)
"turbine" "0.0.1" "replace";

/* replace all occurences of substring with rep_string */
@pure
(string o) replace_all (string s, string substring, string rep_string, int start_index)
"turbine" "0.0.1" "replace_all";

#endif
