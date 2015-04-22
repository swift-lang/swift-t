
@sideeffectfree @deterministic
app (file dst) cp (file src) {
    "cp" @src @dst;
}

main {
    // Test arrays of files
    // Test pipelines of app commands
    file x = input_file("635-rand.tmp");
    file y<"635-rand-end" + ".tmp">;
    file y2; // should be optimised out
    
    file A[];

    int N = 50;
    foreach i in [1:N] {
      file z;
      if (i == 1) {
        z = x;
      } else {
        z = cp(x);
      }
      A[i] = z;
    }
    y = A[N];
    y2 = y;
}
