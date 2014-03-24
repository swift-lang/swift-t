
all: prog-swift-main pkgIndex.tcl prog-swift.tcl

### Settings:

TCL = $(HOME)/sfw/tcl-8.6.0
TCL_VERSION = 8.6

CFLAGS = -I $(TCL)/include -fPIC -g -std=gnu99

LIBS = -L $(TCL)/lib -l tcl$(TCL_VERSION) \
		-Wl,-rpath -Wl,$(TCL)/lib

prog-c: prog-c.o swift-main.o
	$(CC) -o $(@) $(^) \
		$(LIBS)

### Tcl:

pkgIndex.tcl: make-package.tcl libswift_main.so
	tclsh make-package.tcl > $(@)

libswift_main.so: swift-main.o helpers.o
	$(CC) -shared -o $(@) \
		$(^) \
		$(LIBS)

### Swift:

prog-swift.tcl: prog-swift.swift
	stc -r $(PWD) $(<)

### Tests:

run: all pkgIndex.tcl
	@echo Running Pure C:
	./prog-swift-main arg1 arg2 output.data
	@echo
	@echo Running Tcl:
	TCLLIBPATH=$(PWD) tclsh prog-tcl.tcl
	@echo
	@echo Running Swift:
	TURBINE_USER_LIB=$(PWD) turbine -l prog-swift.tcl

### Clean:

clean:
	@rm -fv prog-swift-main *.o
	@rm -fv swift-main_wrap.c
	@rm -fv pkgIndex.tcl
	@rm -fv prog-swift.tcl

.PHONY: all clean
