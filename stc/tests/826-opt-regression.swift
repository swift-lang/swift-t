


main {

  int major_start = id(0);
  int x;

  @waitonall
  for (int cycle = major_start, x = 0;
           cycle < major_start + 10;
           cycle = cycle + 1, x = y) {

    y = loop(cycle, x);
  }

  trace(x);
}


(int o) id (int i) {
  o = i;
}


(int o) loop(int cycle, int x) {
  o = cycle + x;
}
