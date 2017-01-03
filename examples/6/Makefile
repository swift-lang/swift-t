
all: prog-f90 prog-cxx pkgIndex.tcl prog-swift.tcl

INCLUDES = -I . ${TCL_INCLUDE_SPEC}

LIBS = $(TCL_LIB_SPEC) \
	-Wl,-rpath -Wl,$(TCL)/lib

### Settings:
ifeq (,$(filter clean,$(MAKECMDGOALS)))
# We skip settings if this is just'make clean'
ifeq (${COMPILER},)
$(warning Set COMPILER!)
endif
ifeq (${COMPILER},GNU)
CXX = g++
CXXFLAGS = $(INCLUDES) -fPIC -g
FC = gfortran
FFLAGS = -ffree-form -std=f2003 -cpp -I . -fPIC -fmax-errors=3 -g
LDFLAGS = -lgfortran
SHARED = -shared 
endif
ifeq (${COMPILER},BGQ-GNU)
# CXX=powerpc64-bgq-linux-g++
# CXXFLAGS = $(INCLUDES) -g
# FC=powerpc64-bgq-linux-gfortran
# FFLAGS=-ffree-form -cpp -I . -fPIC -fmax-errors=3 -g # -std=f2003 
endif
ifeq (${COMPILER},BGQ)
CXX = /soft/compilers/wrappers/xl/mpixlcxx
CXXFLAGS = $(INCLUDES) -qpic -g -qnoeh 
FC = /soft/compilers/wrappers/xl/mpixlf90_r
FFLAGS = -I . -qpic -g
LDFLAGS = -qnoeh -L/soft/compilers/ibmcmp-nov2013/xlf/bg/14.1/lib64 \
	  -lxlopt -lxlf90 -lxlfmath -lxl \
          -L/soft/compilers/ibmcmp-nov2012/lib64/bg -libmc++
SHARED = -qmkshrobj
endif
endif # make clean

### FORTWRAP

FORTWRAP_OUT = FortFuncs.h FortFuncs.cpp FortWrap.h \
	string_array.h string_array.cpp CppWrappers.f90

$(FORTWRAP_OUT): func.f90
#	which fortwrap.py
	fortwrap.py $(<)
ifeq (${COMPILER},BGQ)
#	Fixes for BG/Q:
	sed -i s/_MOD/_NMOD/ $(FORTWRAP_OUT)
#       This DEALLOCATE fails on the BG/Q
#       This "fix" creates a memory leak but I'm stumped. -Justin
	sed -i s/DEALLOCATE/!DEALLOCATE/ CppWrappers.f90
endif

### SWIG

func_wrap.cxx: FortFuncs.h string_array.h func.i
	swig -c++ -includeall func.i
	sed -i s/F_Init/Func_Init/ func_wrap.cxx

### C++

%.o: %.cxx
	$(CXX) -c $(CXXFLAGS) $(<)

# Extra dependency:
prog-cxx.o: prog-cxx.cxx $(FORTWRAP_OUT)

%.o: %.cpp
	$(CXX) -c $(CXXFLAGS) $(<)

### Fortran

%.o: %.f90
	$(FC) -c $(FFLAGS) $(<)

# Extra dependency:
prog-f90.o: prog-f90.f90 func.o

### Programs:

prog-f90: $(OBJS) prog-f90.o func.o
	$(CXX) -o $(@) prog-f90.o func.o  \
		$(LIBS) $(LDFLAGS)

prog-cxx: prog-cxx.o string_array.o CppWrappers.o FortFuncs.o
	$(CXX) -o $(@) prog-cxx.o string_array.o CppWrappers.o FortFuncs.o func.o \
		$(LIBS) $(LDFLAGS)

### Tcl:

pkgIndex.tcl: make-package.tcl libfunc.so
	tclsh make-package.tcl > $(@)

libfunc.so: func_wrap.o string_array.o CppWrappers.o FortFuncs.o func.o helpers.o
	$(CXX) $(SHARED) -o $(@) \
		func_wrap.o string_array.o CppWrappers.o FortFuncs.o func.o helpers.o \
		$(LIBS) $(LDFLAGS)

### Swift:

prog-swift.tcl: prog-swift.swift
	stc -r $(PWD) $(<)

### Tests:

run: all
	@echo Fortran
	./prog-f90 arg1 arg2 output.data
	@echo
	@echo C++
	./prog-cxx arg1 arg2 arg3
	@echo
	@echo Tcl
	TCLLIBPATH=$(PWD) tclsh prog.tcl
	@echo
	@echo Swift
	TURBINE_USER_LIB=$(PWD) turbine -l prog-swift.tcl

### Clean:

clean:
	@rm -fv *.mod CppWrappers.* .fortwrap InterfaceDefs.h
	@rm -fv prog-f90 prog-cxx *.o
	@rm -fv $(FORTWRAP_OUT) func_wrap.cxx
	@rm -fv pkgIndex.tcl

.PHONY: all clean
