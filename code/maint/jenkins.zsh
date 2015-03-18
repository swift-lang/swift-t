#!/bin/zsh

# ADLB JENKINS.ZSH
# Run on the Jenkins server
# Installs ADLB; runs its tests

set -eu

echo
echo "maint/jenkins.zsh ..."
echo

if [[ ! -d /tmp/mpich-install ]]
then
  print "MPICH disappeared!"
  print "You must manually run the MPICH Jenkins test to restore MPICH"
  exit 1
fi

rm -rf autom4te.cache
rm -rf /tmp/exm-install/lb

set -x
PATH=/tmp/mpich-install/bin:$PATH

echo MPICC:
which mpicc
mpicc -show
echo

./bootstrap
mkdir -p /tmp/exm-install
./configure CC=$(which mpicc) --prefix=/tmp/exm-install/lb --with-c-utils=/tmp/exm-install/c-utils
echo
make clean
make V=1 install
# ldd lib/libadlb.so
# make V=1 apps/batcher.x
# ldd apps/batcher.x

SUITE_RESULT="result_aggregate.xml"
rm -fv ${SUITE_RESULT}

rm -fv tests/*.result(.N)

make V=1 -k test_results || true

# Print a message on stderr to avoid putting it in the Jenkins XML
message()
{
  print -u 2 ${*}
}

inspect_results() {
  for test_script in tests/*.sh(.N)
  do
    local test_path=${test_script%.sh}
    local test_name=$(basename ${test_path})
    local test_result="${test_path}.result"
    local test_tmp="${test_path}.tmp"
    local test_out="${test_path}.out"

    print "    <testcase name=\"${test_name}\" >"

    if [[ ! -f "${test_result}" ]]
    then
      # Failure info
      message "Found ERROR in ${result}"
      print "        <failure type=\"generic\">"
      print "Script output file contents:"
      cat ${test_tmp}
      print ""
      print ""
      if [[ -f "${test_out}" ]]
      then
        print "Out file contents:"
        print "<![CDATA["
        cat ${test_out}
        print "]]>"
      fi
      print "        </failure> "
    fi

    print "    </testcase>"
  done
}

inspect_results > ${SUITE_RESULT}

exit 0
