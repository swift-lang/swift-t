# Need at least two workers for additional work types
if [[ -z "${TEST_ADLB_WORKERS-}" || "$TEST_ADLB_WORKERS" -lt 2 ]] ; then
  export TEST_ADLB_WORKERS=2
fi

export TURBINE_A_NEW_WORK_TYPE_WORKERS=1
