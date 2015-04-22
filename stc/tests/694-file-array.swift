// SKIP-O0-TEST for memory leak

file A[];

A[0] = input("/dev/null");

trace_filename(A[0]);

trace_filename(file f) {
  trace(filename(f));
}
