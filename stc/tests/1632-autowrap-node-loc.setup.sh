# Need at least two workers for host targeting
if [[ -z "${TEST_ADLB_WORKERS-}" || "$TEST_ADLB_WORKERS" -lt 2 ]] ; then
  export TEST_ADLB_WORKERS=2
fi


# We request more ranks than a small CI runner has cores.  Open MPI
# refuses to oversubscribe by default ("not enough slots available");
# allow it.  These are Open MPI variables, ignored by other MPIs.
export OMPI_MCA_rmaps_base_oversubscribe=yes                  # Open MPI 4
export PRTE_MCA_rmaps_default_mapping_policy=:oversubscribe   # Open MPI 5
