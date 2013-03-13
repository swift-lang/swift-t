#include <builtins.swift>
#include <string.swift>

// TODO: generate random data
@dispatch=WORKER
(blob o) load(string filename) "turbine" "0.0.4" [
  "set <<o>> [ new_turbine_blob ]; blob_utils_read <<filename>> $<<o>>"
];

@dispatch=WORKER
(float p, int m) evaluate(int i, int j) "turbine" "0.0.4" [
  "set <<p>> [ expr rand() - 0.25 ]; set <<m>> [ expr min(10, int(rand() * 11)) ]"
];

@dispatch=WORKER
(blob o) simulate(blob model, int i, int j) "turbine" "0.0.4" [
  ""
];


@dispatch=WORKER
(blob o) summarize(blob res[]) "turbine" "0.0.4" [
  ""
];

@dispatch=WORKER
(file o) analyze(blob res) "turbine" "0.0.4" [
  ""
];


main () {
  int N_models = toint(argv("N_models"));
  int M = toint(argv("N"));
  int N = toint(argv("M"));
  int S = toint(argv("S"));
  
  blob models[], res[][];
  foreach m in [1:N_models] {
    models[m] = load(sprintf("model%i.data", m));
  }

  foreach i in [1:M] {
    foreach j in [1:N] {
      // initial quick evaluation of parameters
      p, m = evaluate(i, j);
      if (p > 0) {
        // run ensemble of simulations
        blob res2[];
        foreach k in [1:S] {
          res2[k] = simulate(models[m], i, j);
        }
        res[i][j] = summarize(res2);
      }
    }
  }

  // Summarize results to file
  foreach i in [1:M] {
    file out<sprintf("output%i.txt", i)>;
    out = analyze(res[i]);
  }
}
