#!/bin/bash

export TCLLIBPATH=/homes/yadunand/swift-trunk/cog/modules/provider-coaster-c-client/tcl
export TURBINE_USER_LIB=$TCLLIBPATH

if [[ $1 == "all" ]] ; then
    SOURCE=`ls *swift`
elif [[ "$1" == *swift ]]; then
    SOURCE=$1
elif [[ $1 == "clean" ]]; then
    rm *{tcl,out,err,~} -rf &> /dev/null
    echo "Cleaned"
else
    SOURCE=`ls -tr *swift | tail -n 1`
fi

rm RESULT &> /dev/null
for test_case in ${SOURCE[*]}
do
    BASE=${test_case%.swift}
    echo "BASE: $BASE"

    echo "Compiling $1"
    stc $test_case > $BASE.tcl
    [[ "$?" != 0 ]] && echo "Compile failed"

    turbine -n 3 $BASE.tcl
    [[ "$?" != 0 ]] && echo "Execution failed"

    if [[ -x $BASE.check.sh ]]
    then
        echo "Running checks on results from $BASE.swift" | tee -a RESULTS
        ./$BASE.check.sh |  tee -a RESULTS >&1
    fi
done
