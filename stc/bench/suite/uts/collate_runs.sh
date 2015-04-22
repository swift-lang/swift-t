#!/usr/bin/env bash

# Arguments: list of run output files to process

echo 'file	opt_level	processes	elapsed_time	gen_mx	nodes_processed'

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
    gen_mx="" # not in log
    #global_total_nodes_processed=...
    nodes_processed=$(grep -o -E 'global_total_nodes_processed: [0-9]+' $f | grep -o -E '[0-9]+')
  else
    gen_mx=$(grep -o -E -e '--gen_mx=[0-9]+' $f | head -n1 | grep -o -E '[0-9]+')
    nodes_processed="" # Not in log
  fi
  echo -n "$gen_mx	$nodes_processed"
  
  echo
done
