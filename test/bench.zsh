#!/bin/zsh

ITERS=$1
INPUT=$2

TOTAL_START=${SECONDS}

scan -p COMMAND < ${INPUT}
COUNT=${#COMMAND}
printf "COUNT: ${#COMMAND}\n\n"

# set -x

for (( i=1 ; i<=COUNT ; i++ ))
do
  eval "DATA_${i}=()"
done

for (( ITER=1 ; ITER<=ITERS ; ITER++ ))
do
  print "ITER: ${ITER}"
  for (( i=1 ; i<=COUNT ; i++ ))
  do
    START=${SECONDS}
    print "${i} ${COMMAND[i]}"
    eval ${COMMAND[i]}
    STOP=${SECONDS}
    eval "DATA_${i}+=$(( STOP-START ))"
  done
print
done

for (( i=1 ; i<=COUNT ; i++ ))
do

  AVG=$( eval "print -l \$DATA_${i}" | avgz )
  printf "TEST: ${i}\n"
  # eval "print -l \$DATA_${i}"
  print "${AVG}"
  print
done

TOTAL_STOP=${SECONDS}
print "TOTAL_TIME: $(( TOTAL_STOP-TOTAL_START ))"

return 0
