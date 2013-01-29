#include <builtins.swift>
#include <files.swift>

// Test pipelines of app calls

app (file out) echo (string args[]) {
  "echo" args @stdout=out
}

app (file out) sort (file input) {
  "sort" "-o" out input
}

app (file out) head (file input, int n) {
  "head" "-n" n input @stdout=out
}

app (file out) cat (file input) {
  "cat" input @stdout=out
}

main () {
  string args[];

  foreach i in [1:100] {
    args[i] = fromint(200 - i);
  }

  wait (args) {
    file all = echo(args);
    trace("original", readFile(all));
    file top10 = cat(head(sort(all), 10));
    trace("top10", readFile(top10));
  }
}
