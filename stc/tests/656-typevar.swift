// COMPILE-ONLY-TEST
// This should typecheck ok

<T1, T2, T3> (T1 o1[], T2 o2, T3 o3) f(T1 i1, T2 i2, T3 i3[]) "turbine" "0.0" "f";

float o1[];
int o2;
int o3;


(o1, o2, o3) = f(0, 0, [0, 1]);
