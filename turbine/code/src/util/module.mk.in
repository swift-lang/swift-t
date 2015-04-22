
DIR := src/util

TURBINE_SRC += src/util/debug.c
# Not main.c: see there for details

$(DIR)/debug.h: $(DIR)/debug-tokens.tcl $(DIR)/debug-auto.tcl
	$(Q) "  TCL		$(@)"
	$(E) $(TCLSH_LOCAL) src/util/debug-auto.tcl

clean::
	$(E) rm -fv src/util/debug.h
