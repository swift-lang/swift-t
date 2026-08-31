// THIS-TEST-SHOULD-NOT-COMPILE
// bool is not a Swift/T type and is not accepted as a synonym for boolean:
// these declarations name real types, so the type must be spelled in full.

flags(bool loud);

trace(loud);
