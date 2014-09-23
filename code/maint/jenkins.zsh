#!/bin/zsh

C_UTILS=/tmp/exm-install/c-utils
TURBINE=/tmp/exm-install/turbine
STC=/tmp/exm-install/stc
MPICH=/tmp/mpich-install
path+=( $MPICH/bin $TURBINE/bin $STC/bin )

set -eu
# set -x

# Print a message on stderr to avoid putting it in the Jenkins XML
message()
{
  print -u 2 ${*}
}

check_error()
{
  CODE=$1
  MSG=$2
  if (( CODE != 0 ))
  then
    print "Operation failed: ${MSG}"
    print "Exit code: ${CODE}"
    exit 1
  fi
}

./setup.sh
check_error ${?} "setup.sh"

./configure --prefix=$TURBINE               \
            --with-tcl=/usr                 \
            --with-mpi=$MPICH               \
            --with-c-utils=$C_UTILS         \
            --with-adlb=/tmp/exm-install/lb \
            --enable-shared
make clean

make V=1

make V=1 install

# This is allowed to fail- we scan for output below
make test_results || true

cd tests
SUITE_RESULT="result_aggregate.xml"
rm -fv $SUITE_RESULT

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
