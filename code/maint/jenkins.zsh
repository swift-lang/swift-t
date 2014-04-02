#!/bin/zsh

./setup.sh

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
path+=( $MPICH/bin $TURBINE/bin $STC/bin )

set -x

printenv

ls /tmp/mpich-install/lib

LDFLAGS="-L$MPICH/lib -lmpl"                \
./configure --prefix=$TURBINE               \
            --with-tcl=/usr                 \
            --with-mpi=$MPICH               \
            --with-c-utils=$C_UTILS         \
            --with-adlb=/tmp/exm-install/lb \
            --enable-shared
make clean

# echo "Setting exit values to 0 instead of 1"
# cd tests;
# grep "exit 1" *.sh
# sed -i 's/exit 1/echo "SOMETHING_BAD";exit 0/g' *.sh
# grep "exit 1" *.sh
# cd ..;

make V=1

## Results aggregation
make test_results || true
cd tests

SUITE_RESULT="result_aggregate.xml";
rm $SUITE_RESULT > /dev/null 2>&1 ;

echo "<testsuites>" >> $SUITE_RESULT
for result in `ls *.result`
do
    cat $result | grep "SOMETHING_BAD" > /dev/null ;
    #if [[ $? == 0 ]]
    #then
    #    echo "$result : result is BAD "
    #else
    #    echo "$result : result is GOOD "
    #fi
    cat $result | grep "SOMETHING_BAD" >/dev/null ;
    status=${?};
    if [[ $status == 0 ]]
    then
        echo "    <testcase name=\"${result}\" >"     >> $SUITE_RESULT
        echo "        <failure type=\"generic\">"     >> $SUITE_RESULT
        echo "Result file contents:"                  >> $SUITE_RESULT
        cat $result                                   >> $SUITE_RESULT
        echo ""                                       >> $SUITE_RESULT
        echo ""                                       >> $SUITE_RESULT
        echo "Out file contents:"                     >> $SUITE_RESULT
        echo "<![CDATA["                              >> $SUITE_RESULT
        cat ${result%.result}.out                     >> $SUITE_RESULT
        echo "]]>"                                    >> $SUITE_RESULT
        echo "        </failure> "                    >> $SUITE_RESULT
        echo "    </testcase>" >> $SUITE_RESULT
    else
        echo "    <testcase name=\"${result}\" />"    >> $SUITE_RESULT
    fi
done;
echo "</testsuites>" >> $SUITE_RESULT;
