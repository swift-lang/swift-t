import string;
import blob;
import sys;

// TODO: generate random data
@dispatch=WORKER
(blob o) load(string filename) "blob_task" "0.0" [
  "set <<o>> [ blob_task::random_blob 65536 ]; puts \"loaded <<filename>>\""
];

@dispatch=WORKER
(float p, int m) evaluate(int i, int j, int NModels) "turbine" "0.0.4" [
  "set <<p>> [ expr rand() - 0.5 ]; set <<m>> [ expr min(<<NModels>>-1, int(rand() * <<NModels>>)) ]"
];


// Sleep for average of 5ms
// TODO: blob with random perturbations
@dispatch=WORKER
(blob o) simulate(blob model, int i, int j) "lognorm_task" "0.0" [
  "lognorm_task::lognorm_task_impl <<i>> <<j>> 5.52 1; set <<o>> [ blob_task::perturb <<model>> ]"
];


@dispatch=WORKER
(blob o) summarize(blob res[]) "blob_task" "0.0" [
  "set <<o>> [ blob_task::xor_blobs <<res>> ]"
];

@dispatch=WORKER
(blob o) analyze(blob res[]) "blob_task" "0.0" [
  "set <<o>> [ blob_task::xor_blobs <<res>> ]"
];

@dispatch=WORKER
write_out(blob res, string filename) "turbine" "0.0.4" [
  "puts \"Wrote <<filename>>\""
];


main () {
  N_models = toint(argv("N_models"));
  M = toint(argv("N"));
  N = toint(argv("M"));
  S = toint(argv("S"));
  
  blob models[], res[][];
  foreach m in [1:N_models] {
    models[m] = load(sprintf("model%i.data", m));
  }

  foreach i in [1:M] {
    foreach j in [1:N] {
      // initial quick evaluation of parameters
      p, m = evaluate(i, j, N_models);
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
    write_out(analyze(res[i]), sprintf("output%i.txt", i));
  }
}
