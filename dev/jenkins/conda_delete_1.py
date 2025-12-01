#!/usr/bin/env python3

"""
CONDA DELETE 1
Randomly delete one of the oldest downloads in your conda cache
"""

import datetime
import os
import random
import tools


def main():
    args = parse_args()
    os.chdir(args.directory)
    delete_one(args)


def parse_args():
    import argparse
    description = "Delete an old Anaconda download"
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--rate", default=0.5, type=float,
                        help="Fractional chance to delete")
    parser.add_argument("directory",
                        help="Conda cache directory")
    args = parser.parse_args()
    return args


def delete_one(args):

    # 1: Find the Conda files
    import glob
    L = glob.glob("*.conda")
    if len(L) == 0:
        print("found nothing!")
        exit()

    # 2: Count the Conda files by package
    # Map from filename to count of versions on disk
    counts = {}
    for f in L:
        name = get_name(f)
        if name not in counts:
            counts[name] = 1
        else:
            counts[name] += 1
    # for name in counts.keys():
    #     print(name + " " + str(counts[name]))
    most = sorted(counts.values())[-1]
    print("most versions:     %i" % most)
    if most == 1:
        tools.stop("no duplicates!")

    # 3: Find the packages with the most versions and select 1
    tops = [ t for t in counts.keys() if counts[t] == most ]
    print("eligible packages: %i" % len(tops))
    random.shuffle(tops)
    selection = tops[0]
    print("selected package:  " + selection)

    # 4: Sort the versions of the selected package and report
    import fnmatch
    V = fnmatch.filter(L, selection + "*.conda")
    # List of pairs (timestamp, filename):
    times = []
    for f in V:
        stats = os.stat(f)
        times.append((stats.st_mtime, f))
    times = sorted(times)
    for pair in times:
        dt = datetime.datetime.fromtimestamp(pair[0])
        timestamp = dt.strftime("%Y-%m-%d %H:%M")
        print("  " + timestamp + " " + pair[1])

    # 5: After reporting, see if we really want to delete:
    if random.random() > args.rate:
        tools.stop("not deleting due to random chance.")

    # 6: Actually remove the oldest version of the selected package!
    print("removing:")
    oldest_file = times[0][1]
    print("oldest file: " + oldest_file)
    oldest_dir = oldest_file[0:-6]
    print("oldest dir:  " + oldest_dir)
    import shutil
    shutil.rmtree(oldest_dir)
    os.remove(oldest_file)


def get_name(s):
    tokens = s.split("-")
    good = tokens[0:-2]
    return "-".join(good)


if __name__ == "__main__": main()
