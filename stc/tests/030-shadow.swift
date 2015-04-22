import io;

main {
  string trace = "Hello world";
  trace2(trace);

  string int = "not an int";
  trace2(int);
}

trace2(string out) {
  trace(out);
}
