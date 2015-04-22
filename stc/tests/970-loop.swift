
/*
 * Based on PIPS app
 *
 * usage: 970-loop -N=... -C=...
 * Number of loops is N x C
 */

import io;
import stats;
import sys;
import string;
import mpe;

(float result[]) calc_cutoffs(float step)
{
  int count = toint(argv("C"));
  float c = itof(count);
  result[0] = 0.0;
  foreach i in [1:count-2]
  {
    printf("i: %i", i);
    result[i] = itof(i)/c;
  }
  result[count-1] = 1.0;
}

main
{
  // problem data
  string data = "/home/wozniak/PIPS-data-2/";
  // string dataPath = data + "uc_dumps/uc_raw-4h";
  string dataPath = data + "4h_dump/uc_4h";
  string solutionPath = data + "primalsol_conv8";
  int nScenarios = toint(argv("N"));
  int nCutoffs = toint(argv("C"));

  float cutoffs[] = calc_cutoffs(0.1);

  foreach cutoff in cutoffs
  {
    printf("cutoff: %0.5f", cutoff);

    float r = 2.0 * cutoff;
    float A[];
    foreach i in [0 : nScenarios-1]
    {
      // Create some artificial work
      void v = sleep(1);
      int z = zero(v);
      A[i] = itof(i) + r + itof(z);
      printf("scenario: %0.4f %i", cutoff, i);
    }
    float result = sum_float(A);
  }

  // Output some metadata
  int procs = adlb_servers() + turbine_engines() + turbine_workers();
  printf("procs: %i", procs);
  metadata(sprintf("procs: %i", procs));
}
