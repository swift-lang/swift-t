#!/bin/zsh

# Copyright 2013 University of Chicago and Argonne National Laboratory
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License


SUITE_RESULT="result_aggregate.xml";
rm $SUITE_RESULT > /dev/null 2>&1 ;


if [[ ! -f  results.out ]]
then
  print "Could not find results.out!"
  return 1
fi

echo "<testsuites>" >> $SUITE_RESULT

STC_OUT=`ls *stc.out`;
OUT=`ls *[^stc].out`;
COUNT_STC=0;

LINES=($(cat RESULTS_FILE));
LINE_NUMBER=0;
echo ${#LINES[@]};

while read line
do
    if [[ $line == test:* ]]
    then
	a=( $line );
	T_count=${a[1]};
    elif [[ $line == compiling:* ]]
    then
	a=( $line );
	T_swift=${a[1]};
    elif [[ $line == running:* ]]
    then
	a=( $line );
	T_turbine=${a[1]};
    elif [[ $line == PASSED ]]
    then
	echo "Test count : $T_count";
	echo "Test swift : $T_swift";
	echo "Test turbine : $T_turbine";
	echo "Status: $line"
        echo "  <testcase name=\"${T_swift}\" />"    >> $SUITE_RESULT
    elif [[ $line == FAILED ]]
    then
	echo "Test count : $T_count";
	echo "Test swift : $T_swift";
	echo "Test turbine : $T_turbine";
	echo "Status: $line"
        echo "  <testcase name=\"${T_swift}\" >"     >> $SUITE_RESULT
        echo "     <failure type=\"generic\">"       >> $SUITE_RESULT
	echo "     ${T_swift}   ${T_tcl}"            >> $SUITE_RESULT
	echo "OUTPUT from ${T_swift%.swift}.out"     >> $SUITE_RESULT
	echo "<![CDATA["                             >> $SUITE_RESULT
	cat  ${T_swift%.swift}.out                   >> $SUITE_RESULT
        echo "]]>"                                   >> $SUITE_RESULT
	echo "OUTPUT from ${T_swift%.swift}.stc.out" >> $SUITE_RESULT
	cat  ${T_swift%.swift}.stc.out               >> $SUITE_RESULT
        echo "     </failure> "                      >> $SUITE_RESULT
        echo "  </testcase>" >> $SUITE_RESULT
    fi;
done < RESULTS_FILE ;


echo "</testsuites>" >> $SUITE_RESULT;
exit 0;
