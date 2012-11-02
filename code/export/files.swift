
// FILES.SWIFT

#ifndef FILES_SWIFT
#define FILES_SWIFT

@pure
(string t[]) glob(string s)
"turbine" "0.0.2" "glob";

@pure
(string t) readFile(file f)
"turbine" "0.0.2" "readFile";

@pure
(file t) writeFile(string s)
"turbine" "0.0.2" "writeFile";


#endif // FILES_SWIFT

