"""
Script to generate python build config paths needed to build turbine with
embedded python interpreter support. Requires python 2.6+.
"""
from __future__ import print_function

import sys
import optparse
import sysconfig
import os.path


CONFIG_NAMES = [
    'include-dir', 'include-flags',
    'lib-dir', 'lib-name', 'lib-flags',
    'version', 'version-major', 'version-minor', 'version-suffix']


def print_usage(prog_name):
    print(('Usage: %s --all | ' % prog_name)
          + ' | '.join('--' + name for name in CONFIG_NAMES))


def get_lib_name():
    # LDLIBRARY has format libpythonX.Yz.so
    lib_file = sysconfig.get_config_var('LDLIBRARY')
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


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print_usage(sys.argv[0])
        sys.exit(1)
    if sys.argv[1] == '--help':
        print_usage(sys.argv[0])
        sys.exit(0)
    if sys.argv[1] == '--all':
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
                print("%-14s" % name, get_config_value(name))
            else:
                print(get_config_value(name))
        except ValueError:
            print_usage(sys.argv[0])
            sys.exit(1)
