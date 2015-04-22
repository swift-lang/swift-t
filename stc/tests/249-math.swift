import math;
import assert;

/* constants */
assert(abs(PI - 3.1416) < 0.0001, "PI");
assert(abs(E - 2.7183) < 0.0001, "E");

assert(abs(cbrt(100) - 4.641588833612789) < 1e15, "cbrt");

/* logarithms */
assert(abs(log10(1000) - 3.0) < 1e-15, "log10");
assert(abs(ln(E ** 2) - 2.0) < 1e-15, "ln");
assert(abs(log(5**4, 25) - 2.0) < 1e-15, "log25");

/* trig */
assert(abs(sin(PI/2) - 1.0) < 1e-15, "sin");
assert(abs(cos(PI) - -1.0) < 1e-15, "cos");
assert(abs(tan(PI/4) - 1.0) < 1e-15, "tan");
assert(abs(asin(PI/4) - 0.9033391107665127) < 1e-15, "asin");
assert(abs(acos(0) - 1.5707963267948966) < 1e-15, "acos");
assert(abs(atan(PI) - 1.2626272556789118) < 1e-15, "atan");
assert(abs(atan2(-1, 4) - -0.24497866312686414) < 1e-15, "atan2");

