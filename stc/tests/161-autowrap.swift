import assert;

// Test the auto-wrapping functionality
@pure 
(void o1, int o2) void_fn(void v) "turbine" "0.0.1" [ 
    "set <<o2>> 1 "
];

main {
    void x;
    int y;

    void a; 
    x, y = void_fn(a);
    a = make_void();


    assertEqual(y, 1, "y");
    wait (x) {
      trace("x was set");
    }
}
