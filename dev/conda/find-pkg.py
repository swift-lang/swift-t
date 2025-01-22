
# FIND PKG PY
# Find the Anaconda package name
# This is auto-generated internally by 'conda build'
#      and reported to the user in repodata.json
# We can also find the pkg name from the message
#      from 'conda build' saying "anaconda upload"

import argparse, json, os, sys

parser = argparse.ArgumentParser(description="Show the package name")
parser.add_argument("filename", help="The repodata.json file")
parser.add_argument("-v", action="store_true",
                    help="Make verbose")
args = parser.parse_args()

def fail(msg):
    sys.stderr.write("find-pkg.py: " + msg + "\n")
    exit(1)

if not os.path.exists(args.filename):
    fail("not found: " + args.filename)

with open(args.filename, "r") as fp:
    J = json.load(fp)

if args["v"]:
    print(str(J))
    print(str(J["packages"]))

P = J["packages"]
if len(P) != 1:
    print("find-pkg.py: found packages: " + str(P))
    fail("package count is %i not 1!" % len(P))

pkg = list(P.keys())[0]
print(pkg)
