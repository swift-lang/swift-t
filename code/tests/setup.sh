
# Re-usable test setup lines
# Helps automate selection of process mode (engine, server, worker)
# Prints the "SETUP:" header in the *.out file

export ADLB_EXHAUST_TIME=1

if [[ ${TURBINE_ENGINES} == "" ]]
then
  export TURBINE_ENGINES=1
fi

if [[ ${TURBINE_WORKERS} == "" ]]
then
  export TURBINE_WORKERS=1
fi

if [[ ${ADLB_SERVERS} == "" ]]
then
  export ADLB_SERVERS=1
fi

PROCS=$(( ${TURBINE_ENGINES} + ${TURBINE_WORKERS} + ${ADLB_SERVERS} ))

display()
{
  T=$1
  I=$2
  J=$3
  V=$( eval echo \$${T} )
  printf "%-16s %3i RANKS: %3i - %3i\n" ${T}: ${V} ${I} ${J}
}

TURBINE_RANKS=$(( ${TURBINE_ENGINES} + ${TURBINE_WORKERS} ))

echo SETUP:
date "+%m/%d/%Y %I:%M%p"
display TURBINE_ENGINES 0 $(( TURBINE_ENGINES-1 ))
display TURBINE_WORKERS ${TURBINE_ENGINES} $(( TURBINE_RANKS-1 ))
display ADLB_SERVERS ${TURBINE_RANKS} $(( PROCS-1 ))
echo PROCS: ${PROCS}
echo
