
"""
PYTHON CONFIG PY

Generates Python build config paths needed to build Turbine with
embedded Python interpreter support using Python sysconfig .

Requires Python 2.6+

Usage:
  --list    Show all configuration variables available from sysconfig
  --all     Show all configuration variables relevant to Turbine
  --debug   Like --all but pretty-print and include some debug info
            on stderr
  --help    Show individual configuration variable selection flags
  --flag*   Show one or more individual configuration variables
"""

from __future__ import print_function

import sys
import optparse
import sysconfig
import os.path


debug = False

CONFIG_NAMES = [
    'include-dir', 'include-flags',
    'lib-dir', 'lib-name', 'lib-flags',
    'version', 'version-major', 'version-minor', 'version-suffix']


def print_usage(prog_name):
    print(('Usage: %s --all | ' % prog_name)
          + ' | '.join('--' + name for name in CONFIG_NAMES))


def align_kv(k, v):
    indent = "  " if debug else ""
    print("%s%-14s %s" % (indent, k, v))


def debug_kv(k, v):
    if not debug: return
    sys.stderr.write("  %-14s %s\n" % (k, v))


def get_lib_name():
    # LDLIBRARY has format libpythonX.Yz.so
    # Bryce used LDLIBRARY c. 2017
    # Seems that we should now use LIBRARY as of 2024-09-09
    lib_file = sysconfig.get_config_var('LIBRARY')
    debug_kv("LIBRARY", lib_file)
    lib_name = os.path.splitext(lib_file)[0]
    if lib_name.startswith('lib'):
        lib_name = lib_name[3:]
    return lib_name


def get_config_value(name):
    if name == 'include-dir':
        value = sysconfig.get_config_var('INCLUDEPY')
    elif name == 'include-flags':
        value = '-I' + sysconfig.get_config_var('INCLUDEPY')
    elif name == 'lib-dir':
        value = sysconfig.get_config_var('LIBDIR')
    elif name == 'lib-name':
        value = get_lib_name()
    elif name == 'lib-flags':
        libs = ['-l' + get_lib_name()]
        libs += sysconfig.get_config_var('LIBS').split()
        libs += sysconfig.get_config_var('SYSLIBS').split()
        value = " ".join(libs)
    elif name == 'version':
        value = sysconfig.get_python_version()
    elif name == 'version-major':
        value = sysconfig.get_python_version().split('.')[0]
    elif name == 'version-minor':
        value = sysconfig.get_python_version().split('.')[1]
    elif name == 'version-suffix':
        value = sysconfig.get_config_var('ABIFLAGS') or ""
    else:
        raise ValueError('Unknown config name: %s' % name)
    if value is None:
        # NOTE: some values like version-suffix can be empty
        print('ERROR: missing config value for "%s"' % name)
        sys.exit(1)
    return value


def show_debug(names):
    global debug
    debug = True
    for name in names:
        align_kv(name, get_config_value(name))


def show_list():
    global debug
    debug = True
    for k, v in sysconfig.get_config_vars().items():
        align_kv(k, v)

if __name__ == '__main__':
    if len(sys.argv) < 2:
        print_usage(sys.argv[0])
        sys.exit(1)
    elif sys.argv[1] == '--help':
        print_usage(sys.argv[0])
        sys.exit(0)
    elif sys.argv[1] == '--debug':
        show_name = True
        names = CONFIG_NAMES
        show_debug(names)
        sys.exit(0)
    elif sys.argv[1] == '--list':
        show_list()
        sys.exit(0)
    elif sys.argv[1] == '--all':
        show_name = True
        names = CONFIG_NAMES
    else:
        show_name = False
        names = []
        for arg in sys.argv[1:]:
            if not arg.startswith('--'):
                print_usage(sys.argv[0])
                sys.exit(1)
            names.append(arg[2:])

    for name in names:
        try:
            if show_name:
                print(name, get_config_value(name))
            else:
                print(get_config_value(name))
        except ValueError:
            print_usage(sys.argv[0])
            sys.exit(1)
