
all: ex2 pkgIndex.tcl power-swift.tcl

### Settings:

#TCL = $(HOME)/sfw/tcl-8.6.0
#TCL_VERSION = 8.6
JAVA = $(HOME)/jdk1.7.0_07/bin/java
TCL = $(HOME)/tcl-install
TCL_VERSION = 8.5

CFLAGS = -I $(TCL)/include -fPIC -g -std=gnu99

LIBS = -L $(TCL)/lib -l tcl$(TCL_VERSION) \
		-Wl,-rpath -Wl,$(TCL)/lib

prog-c: ex2.o ex2forswift.o
	$(CC) -o $(@) $(^) \
		$(LIBS)

### Tcl:
pkgIndex.tcl: make-package.tcl libpower_main.so
	tclsh make-package.tcl > $(@)

libpower_main.so: extension.o ex2forswift.o
	$(CC) -shared -o $(@) \
		$(^) \
		$(LIBS)

### Swift:

power-swift.tcl: power-swift.swift
	stc -j $(JAVA) -r $(PWD) $(<)

### Tests:

run: all pkgIndex.tcl
	@echo Running Swift:
	TURBINE_USER_LIB=$(PWD) turbine -l prog-swift.tcl

### Clean:

clean:
	@rm -fv ex2 prog-swift-main *.o *.so
	@rm -fv swift-main_wrap.c
	@rm -fv pkgIndex.tcl

.PHONY: all clean
