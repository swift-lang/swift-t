
# linux-64 DEPS SH

# For some reason zlib is not found on linux-64, Python 3.8
#     as of 2024-12-11:
if [[ $PYTHON_VERSION == 3.8* ]] USE_ZLIB=1
