#!/bin/bash

export TCLLIBPATH=/homes/yadunand/swift-trunk/cog/modules/provider-coaster-c-client/tcl
export TURBINE_USER_LIB=$TCLLIBPATH
export TURBINE_INSTALL=/homes/yadunand/bin/exm-trunk/sfw/turbine/branches/issue-503/code
TURBINE=/homes/yadunand/bin/exm-trunk/sfw/turbine/branches/issue-503/code/bin
STC=/homes/yadunand/bin/exm-trunk/sfw/stc/branches/issue-526/code/bin
export PATH=$TURBINE:$STC:$PATH

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

#export VALGRIND=valgrind
export VALGRIND=""
for test_case in ${SOURCE[*]}
do
    BASE=${test_case%.swift}
    echo "BASE: $BASE"

    echo "Compiling $1"
    stc $test_case > $BASE.tcl
    [[ "$?" != 0 ]] && echo "Compile failed"

    # Correct one line for working with older turbine ?
    sed -i '/^turbine/s/\ \"Swift\"//' $BASE.tcl

    #strace -f turbine -n 3 $BASE.tcl -d=$PWD
    turbine -n 3 $BASE.tcl -d=$PWD
    STATUS="$?"

    if [[ "$STATUS" == 0 ]]
    then
        echo "STATUS: SUCCESS - $BASE.swift"
    else
        echo "STATUS: FAIL - $BASE.swift"
    fi

    #TURBINE_PID=$!
    #echo "TURBINE PID : $TURBINE_PID"
    #exit 0

    #[[ "$STATUS" == 124 ]] && echo "Execution timed out $BASE.swift"

    if [[ -x $BASE.check.sh ]]
    then
        echo "Running checks on results from $BASE.swift" | tee -a RESULTS
        ./$BASE.check.sh |  tee -a RESULTS >&1
    fi
done
