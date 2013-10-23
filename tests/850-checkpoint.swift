// SKIP-THIS-TEST

main {
 
 trace(f(1));
 trace(f(2));

 foreach i in [1:100] {
    trace(f(i));
 }
}


@checkpoint
(int o) f ( int i) {

    o = i + 1;
}
