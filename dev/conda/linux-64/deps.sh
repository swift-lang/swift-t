
# linux-64 DEPS SH

# For some reason zlib is not found on linux-64, Python 3.8
#     as of 2024-12-11:
# Needed for all linux-64 as of 2024-12-17
# if [[ $PYTHON_VERSION == 3.8* ]]
export USE_ZLIB=1
