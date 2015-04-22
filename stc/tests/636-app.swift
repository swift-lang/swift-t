// Check forward refs work
main {

  file x<"636-out.txt"> = echo("hello");
}

app (file dst) echo (string s) {
    "./636-echo.sh" @dst (id(s));
}

(string o) id (string i) {
  o = i;
}
