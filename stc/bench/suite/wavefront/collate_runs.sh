#!/usr/bin/env bash

# Arguments: list of run output files to process

echo 'file	opt_level	processes	elapsed_time	N	mu	sigma'

for f in "$@"
do
  echo -n "$f	"

  OPT_REGEX='(adlb|O[0-3])'
  PROC_REGEX='p[0-9]+'
  params=$(echo $(basename $f) | grep -o -E "${OPT_REGEX}\.${PROC_REGEX}")
  opt=$(echo $params | grep -o -E "${OPT_REGEX}")
  procs=$(echo $params | grep -o -E "${PROC_REGEX}" | sed 's/p//')
  echo -n "$opt	$procs	"

  ELAPSED_STR="ADLB Total Elapsed Time: "
  elapsed=$(grep -o -E "${ELAPSED_STR}[0-9.]+" $f | head -n1 | 
            grep -o -E "[0-9.]+")
  echo -n "$elapsed	"

  if [ "$opt" = adlb ]
  then
    # ADLB: no data
    n_str=""
    mu_str=""
    sigma_str=""
  else
    # Swift:
    # WAVEFRONT N=2000 mu=-9 sigma=1
    info=$(grep -m1 -o -E "WAVEFRONT N=[0-9]+ mu=[-0-9.]+ sigma=[-0-9.]+" $f)
    n_str=$(echo $info | grep -o -E "N=[0-9]+" | grep -o -E "[0-9]+")
    mu_str=$(echo $info | grep -o -E "mu=[-0-9.]+" | grep -o -E "[-0-9.]+")
    sigma_str=$(echo $info | grep -o -E "sigma=[-0-9.]+" | grep -o -E "[-0-9.]+")
  fi

  echo -n "$n_str	$mu_str	$sigma_str"
  echo
done
