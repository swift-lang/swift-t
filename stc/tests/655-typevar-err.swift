// THIS-TEST-SHOULD-NOT-COMPILE

// Don't support unions with type vars
<T> f(T|bag<T> x) "turbine" "0.0" "f";

f(1);
