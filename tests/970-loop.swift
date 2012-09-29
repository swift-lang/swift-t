
/*
 * Based on PIPS app
 * Exposes bug in placement of slot_drop instruction
 */

#include <builtins.swift>
#include <io.swift>
#include <stats.swift>
#include <sys.swift>

(float result[]) cutoffs(float step)
{
  for (int i = 0; i < 10; i = i + 1)
  {
    result[i] = step*itof(i);
  }
}

main
{
  // problem data
  string data = "/home/wozniak/PIPS-data-2/";
  // string dataPath = data + "uc_dumps/uc_raw-4h";
  string dataPath = data + "4h_dump/uc_4h";
  string solutionPath = data + "primalsol_conv8";
  int nScenarios = toint(argv("N"));

  float cutoffs[] = cutoffs(0.1);

  foreach cutoff in cutoffs
  {
    float r = 2.0 * cutoff;

    float v[];
    foreach i in [0 : nScenarios-1]
    {
      v[i] = itof(i) + r;
    }
    float result = sum_float(v);
  }

  // Output some metadata
  int procs = adlb_servers() + turbine_engines() + turbine_workers();
  printf("procs: %i", procs);
}
