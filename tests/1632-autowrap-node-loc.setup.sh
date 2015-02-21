# Need at least two workers for host targeting
if [[ -z "${TEST_ADLB_WORKERS-}" || "$TEST_ADLB_WORKERS" -lt 2 ]] ; then
  export TEST_ADLB_WORKERS=2
fi

