#!/bin/zsh

# Generate linear-*.tcl, a tcl-turbine test case

COUNT=$1

if [[ ${COUNT} == "" ]]
then
  print "Not given: COUNT"
  return 1
fi

OUTPUT="linear-${COUNT}.tcl"

alias spacer='print >> ${OUTPUT}'

# Header
{
  print "package require turbine 0.1"
  print "turbine_init"
} > ${OUTPUT}

spacer

# Data declarations
for (( i=0 ; i<COUNT ; i++ ))
do
  print "turbine_file ${i} ${i}.txt"
done >> ${OUTPUT}

spacer

# Task dependencies
print "turbine_rule 0 0 { } { 0 } { touch 0.txt }" >> ${OUTPUT}
for (( i=1 ; i<COUNT ; i++ ))
do
  PREV=$(( i-1 ))
  printf "turbine_rule ${i} ${i} { ${PREV} } { ${i} } "
  print "{ touch ${i}.txt }"
done >> ${OUTPUT}

spacer

# Footer

{
  print "turbine_engine"
  print "turbine_finalize"
  print "puts OK"
} >> ${OUTPUT}

print "wrote: ${OUTPUT}"

return 0
