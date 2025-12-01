
def print_list(L):
    for t in L:
        print(str(t))


def abort(msg):
    stop(msg, code=1)


def stop(msg, code=0):
    print(msg)
    exit(code)
