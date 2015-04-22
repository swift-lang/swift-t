#!/bin/zsh

# Jenkins script - run Turbine test suite

set -eu
# set -x

source maint/jenkins-configure.zsh

rm -fv ./*.lastrun(.N)

# This is allowed to fail- we scan for output below
make -k test_results || true

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
  print "<testsuite name=\"turbine\">"
  for test_lastrun in ./*.lastrun(.N)
  do
    local test_path=${test_lastrun%.lastrun}
    local test_name=$(basename ${test_path})
    local test_result="${test_path}.result"
    local test_tmp="${test_path}.tmp"
    local test_out="${test_path}.out"

    if [[ -f "${test_result}" ]]
    then
      # Success:
      print "    <testcase name=\"${test_name}\"/>"
    else
      message "Failure - ${test_result} not present"
      print "    <testcase name=\"${test_name}\">"
      print "        <failure type=\"generic\">"

      if [[ -f "${test_tmp}" ]]
      then
        print "Result tmp file contents:"
        print "<![CDATA["
        cat "${test_tmp}"
        print "]]>"
        print ""
        print ""
      fi

      if [[ -f "${test_out}" ]]
      then
        print "Out file contents:"
        print "<![CDATA["
        cat "${test_out}"
        print "]]>"
      fi
      print "        </failure> "
      print "    </testcase>"
    fi
  done
  print "</testsuite>"
}

inspect_results > ${SUITE_RESULT}
