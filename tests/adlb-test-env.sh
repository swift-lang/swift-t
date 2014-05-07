# Environment variables for ADLB tests
# Reduce buffer limits to improve test coverage
export ADLB_DEBUG_SYNC_BUFFER_SIZE=4
export ADLB_SYNC_RECVS=3
export ADLB_CLOSED_CACHE_SIZE=8

# Reduce exhaust time to speed up tests and better
# stress-test exhaust checking
export ADLB_EXHAUST_TIME=0.01
