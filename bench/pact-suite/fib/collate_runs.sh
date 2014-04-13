#!/usr/bin/env bash

# Arguments: list of run output files to process

echo 'file	opt_level	processes	elapsed_time	n	fib_n'

for f in "$@"
do
  echo -n "$f	"

  OPT_REGEX='(adlb|O[0-3])'
  PROC_REGEX='p[0-9]+'
  params=$(echo $(basename $f) | grep -o -E "${OPT_REGEX}\.${PROC_REGEX}")
  opt=$(echo $params | grep -o -E "${OPT_REGEX}")
  procs=$(echo $params | grep -o -E "${PROC_REGEX}")
  echo -n "$opt	$procs	"

  ELAPSED_STR="ADLB Total Elapsed Time: "
  elapsed=$(grep -o -E "${ELAPSED_STR}[0-9.]+" $f | head -n1 | 
            grep -o -E "[0-9.]+")
  echo -n "$elapsed	"


  if [ "$opt" = adlb ]
  then
    # ADLB: Fib(44) = 701408733
    out=$(grep -E 'Fib\([0-9]+\) = [0-9]+' $f | head -n1)
    n_str=$(echo $out | grep -o -E 'Fib\([0-9]+\)' | grep -o -E '[0-9]+')
    fib_n_str=$(echo $out | grep -o -E '= [0-9]+' | grep -o -E '[0-9]+')

  else
    # Swift:
    # START: n=36
    # DONE: fib(n)=14930352
    n_str=$(grep -o -E 'START: n=[0-9]+' $f | head -n1 | grep -o -E '[0-9]+')
    fib_n_str=$(grep -o -E 'fib\(n\)=[0-9]+' $f | head -n1 | grep -o -E '[0-9]+')
  fi
  echo -n "$n_str	$fib_n_str"
  
  echo
done
