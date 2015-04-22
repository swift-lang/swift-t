import files;

main {
  file x<"6921-test.txt"> = f();

  string s = read(x);
  trace(s);
}

(file o) f() {
  o = input("6921-file-map.swift");
}
