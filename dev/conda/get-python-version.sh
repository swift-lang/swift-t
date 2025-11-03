
# GET PYTHON VERSION

T=( $( python --version ) )

print "get-python-version.sh: Python version detected: $T"

export PYTHON_VERSION=${T[2]}

# ZSH syntax: Split e.g. "3.12.7" into ( 3 12 7 )
VS=( ${(s:.:)PYTHON_VERSION} )

# Python version, just major.minor: e.g. "3.12"
export PYTHON_VERSION_MM="${VS[1]}.${VS[2]}"

# Get the next Python version (as an upper bound for meta.yaml)
NEXT_MINOR=$[ ${VS[2]} + 1 ]
export PYTHON_NEXT_MM="${VS[1]}.${NEXT_MINOR}"

# Current Python series in meta.yaml range format:
# E.g., >=3.12,<3.13
export PYTHON_SERIES=">=${PYTHON_VERSION_MM},<${PYTHON_NEXT_MM}"
