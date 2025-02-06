
# FIND PKG PY
# Find the Anaconda package name
# This is auto-generated internally by 'conda build'
#      and reported to the user in repodata.json
# We can also find the pkg name from the message
#      from 'conda build' saying "anaconda upload"
# NOTE: Logging output must go on stderr,
#       stdout is captured by the shell script!

import argparse, json, os, sys

parser = argparse.ArgumentParser(description="Show the package name")
parser.add_argument("filename", help="The repodata.json file")
parser.add_argument("-v", action="store_true",
                    help="Make verbose")
args = parser.parse_args()

def say(msg):
    import sys
    sys.stderr.write("find-pkg.py: " + str(msg) + "\n")

def fail(msg):
    say(msg)
    exit(1)

if not os.path.exists(args.filename):
    fail("not found: " + args.filename)

with open(args.filename, "r") as fp:
    J = json.load(fp)

# This key seems to change by version:
if sys.version_info.minor == 8:
    # Python 3.8.18 2025-02-06:
    pkg_key = "packages"
else:
    pkg_key = "packages.conda"

def show_repodata(J):
    say("full JSON:")
    say(J)
    say("key: '%s'" % pkg_key)
    say(J[pkg_key])

if args.v:
    show_repodata(J)

P = J[pkg_key]
if len(P) != 1:
    say("found packages: " + str(P))
    say("package count is %i not 1!" % len(P))
    show_repodata(J)
    fail("FAIL.")

pkg = list(P.keys())[0]
print(pkg)
