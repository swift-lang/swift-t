
# Use ./build.sh to run this

all: prog-f90 prog-cxx pkgIndex.tcl prog-swift.tcl

### Settings:

FFLAGS = -ffree-form -std=f2003 -cpp -I . -fPIC -fmax-errors=3 -g

CXXFLAGS = -I . ${TCL_INCLUDE_SPEC} -fPIC -g

### FORTWRAP

FORTWRAP_OUT = FortFuncs.h FortFuncs.cpp FortWrap.h \
	string_array.h string_array.cpp CppWrappers.f90

$(FORTWRAP_OUT): .fortwrap

.fortwrap: func.f90
	fortwrap.py $(<)
	touch .fortwrap

### SWIG

func_wrap.cxx: FortFuncs.h string_array.h func.i
	swig -c++ -includeall func.i
	sed -i s/F_Init/Func_Init/ func_wrap.cxx

### C++

%.o: %.cxx
	g++ -c $(CXXFLAGS) $(<)

# Extra dependency:
prog-cxx.o: prog-cxx.cxx $(FORTWRAP_OUT)

%.o: %.cpp
	g++ -c $(CXXFLAGS) $(<)

### Fortran

%.o: %.f90
	gfortran -c $(FFLAGS) $(<)

# Extra dependency:
prog-f90.o: prog-f90.f90 func.o

### Programs:

LIBS = $(TCL_LIB_SPEC) -lgfortran \
		-Wl,-rpath -Wl,$(TCL)/lib

prog-f90: $(OBJS) prog-f90.o func.o
	g++ -o $(@) prog-f90.o func.o  \
		$(LIBS)

prog-cxx: prog-cxx.o string_array.o CppWrappers.o FortFuncs.o
	g++ -o $(@) prog-cxx.o string_array.o CppWrappers.o FortFuncs.o func.o \
		$(LIBS)

### Tcl:

pkgIndex.tcl: make-package.tcl libfunc.so
	tclsh make-package.tcl > $(@)

libfunc.so: func_wrap.o string_array.o CppWrappers.o FortFuncs.o func.o helpers.o
	g++ -shared -o $(@) \
		func_wrap.o string_array.o CppWrappers.o FortFuncs.o func.o helpers.o \
		$(LIBS)

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
