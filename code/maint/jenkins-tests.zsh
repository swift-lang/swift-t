#!/bin/zsh

# Jenkins script - run Turbine test suite

path+=( $MPICH/bin $TURBINE/bin )

set -eu
# set -x

# This is allowed to fail- we scan for output below
make test_results || true

cd tests
SUITE_RESULT="result_aggregate.xml"
rm -fv $SUITE_RESULT

# Print a message on stderr to avoid putting it in the Jenkins XML
message()
{
  print -u 2 ${*}
}

inspect_results()
{
  print "<testsuites>"
  for result in *.result
  do
    if grep "ERROR" ${result} > /dev/null
    then
      message "Found ERROR in ${result}"
      print "    <testcase name=\"${result}\" >"
      print "        <failure type=\"generic\">"
      print "Result file contents:"
      cat ${result}
      print ""
      print ""
      print "Out file contents:"
      print "<![CDATA["
      cat ${result%.result}.out
      print "]]>"
      print "        </failure> "
      print "    </testcase>"
    else
      # Success:
      print "    <testcase name=\"${result}\" />"
    fi
  done
  print "</testsuites>"
}

inspect_results > ${SUITE_RESULT}
