
// SKIP-THIS-TEST

import io;

@par @dispatch=WORKER (void v) f(int i) "funcs_610" "0.0" "f";

printf("HI");

@par=3 f(3);
@par=3 f(4);
@par=3 f(5);
