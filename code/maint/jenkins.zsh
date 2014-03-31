#!/bin/zsh

./setup.sh


# Check your mpicc location first!
TURBINE_INSTALL=/tmp/exm-install/turbine/bin
STC_INSTALL=/tmp/exm-install/stc/bin
MPICH=/tmp/mpich-install/bin
export PATH=$MPICH:$TURBINE_INSTALL:$STC_INSTALL:$PATH

set -x

ls /tmp/mpich-install/lib

./configure --prefix=/tmp/exm-install/turbine --with-tcl=/usr --with-mpi=/tmp/mpich-install --with-c-utils=/tmp/exm-install/c-utils --with-adlb=/tmp/exm-install/lb --enable-shared
make clean

echo "Setting exit values to 0 instead of 1"
cd tests;
grep "exit 1" *.sh
sed -i 's/exit 1/echo "SOMETHING_BAD";exit 0/g' *.sh
grep "exit 1" *.sh
cd ..;

## Results aggregator script ##
make test_results ||:
cd tests;

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
