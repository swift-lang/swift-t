
(void z) f1(void v) "turbine" "0.0"
[ "puts f1" ];

(void z) f2(void v) "turbine" "0.0"
[ "puts f2" ];

void v0 = propagate();
void v1 = f1(v0);
void v2 = f2(v1);
