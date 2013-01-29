#include <builtins.swift>
#include <files.swift>
#include <assert.swift>
#include <string.swift>


// Test pipelines of app calls

app (file out) echo (string args[]) {
  "echo" args @stdout=out
}

app (file out) sed(string cmd, file input) {
  "sed" cmd input @stdout=out
}

app (file out) sort (file input) {
  "sort" "-n" "-o" out input
}

app (file out) head (file input, int n) {
  "head" "-n" n input @stdout=out
}

app (file out) cat (file input) {
  "cat" input @stdout=out
}

main () {
  string args[];

  foreach i in [0:99] {
    args[i] = fromint(100 - i);
  }

  wait (args) {
    // make file containing lines from array
    file all = sed("s/ /\\n/g", echo(args));
    trace("original", readFile(all));
    file top10 = cat(head(sort(all), 10));

    // Concatenate 
    string top10list = replace_all(readFile(top10), "\n", ",", 0);
    assertEqual(top10list, "1,2,3,4,5,6,7,8,9,10,", "top10list");
    trace("top10", top10list);

  }
}
