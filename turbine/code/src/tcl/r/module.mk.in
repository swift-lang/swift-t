
# MODULE TCL-R

DIR := src/tcl/r

TCL_R_SRC := $(DIR)/tcl-r.c

ifeq ($(HAVE_R),1)
	TCL_R_OBJS := $(DIR)/rinside-adapter.o
endif

$(DIR)/rinside-adapter.o: $(DIR)/rinside-adapter.C
	$(Q) "  CXX		$(<)"
	$(E) $(CXX) -c -o $(@) $(CPPFLAGS) $(CXXFLAGS) $(INCLUDES) $(<)
