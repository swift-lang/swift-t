
# linux-64 DEPS SH

export USE_NCURSES=1
# For some reason zlib is not found on linux-64, Python 3.8
#     as of 2024-12-11:
# Needed for all linux-64 as of 2024-12-17
export USE_ZLIB=1
