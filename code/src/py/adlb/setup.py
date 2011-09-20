from distutils.core import setup, Extension

adlbmodule = Extension('PyADLB',
  include_dirs = ['/home/wozniak/proj/adlb', '/home/wozniak/sfw/mpich2/include'],
  libraries = ['adlb', 'mpich', 'mpl'],
  library_dirs = ['/home/wozniak/proj/adlb', '/home/wozniak/sfw/mpich2/lib'],
  sources = ['src/py/adlb/adlbmodule.c'],
  extra_compile_args = ['-std=gnu99'])

setup (name = 'PyADLB',
       version = '1.0',
       description = 'This is a demo package',
       ext_modules = [adlbmodule])
