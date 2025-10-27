
# osx-arm64 DEPS SH

USE_ANT=1
USE_CLANG=1
USE_GCC=0
USE_TK=1
USE_ZSH=0

# We export the SPECs so that M4 can use them in m4_getenv()

if [[ ${PYTHON_VERSION} == 3.13.* ]] {
  PYTHON_VERSION=3.13.2
  export SPEC_PYTHON="python==$PYTHON_VERSION"
}

# Prevent MPICH from updating Clang to 19
# SPEC_CLANG='clang==16.0.6'
export SPEC_CLANG='clang==18.1.8'
export SPEC_MPICH='mpich==4.1.2'
export SPEC_TK='tk==8.6.13'
