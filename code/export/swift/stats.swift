
// STATS.SWIFT

// Container numerical aggregate functions

#ifndef STATS_SWIFT
#define STATS_SWIFT

(int result) sum_integer(int A[])
"turbine" "0.0.2" "sum_integer";

(float result) avg(int|float A[])
"turbine" "0.0.2" "avg";

// Population standard deviation
(float result) std(int|float A[])
"turbine" "0.0.2" "std";

(float mean, float std) stats(int|float A[])
"turbine" "0.0.2" "stats";

(int n, float mean, float M2) statagg(int|float A[])
"turbine" "0.0.2" "statagg";

// Aggregate partial statistics
type PartialStats {
  int n; /* number of samples */
  float mean;
  float M2; /* Variance * n_samples */
}

(int n, float mean, float std) stat_combine(PartialStats A[])
"turbine" "0.0.2" "stat_combine";

#endif
