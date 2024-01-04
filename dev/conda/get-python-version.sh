
# GET PYTHON VERSION

T=( $( python --version ) )

print "get-python-version.sh: Python version detected: $T"

export PYTHON_VERSION=${T[2]}
