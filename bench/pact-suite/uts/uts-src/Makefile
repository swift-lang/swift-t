
ifeq ($(TCL_HOME),)
  TCL_HOME = $(HOME)/ExM/inst/tcl-static
endif

RNG_HEADERS = $(shell find rng -name "*.h")

ENABLED_RNG := brg_sha1
ENABLED_RNG_MACRO := BRG_RNG

# Only enable one RNG at a time to avoid link issues
RNG_SRC = rng/$(ENABLED_RNG).c
RNG_OBJS = $(patsubst %.c, %.o, $(RNG_SRC))

OBJS = $(RNG_OBJS) uts.o uts_leaf.o
LIB = libuts.a
LIB_SO = libuts.so

CC = cc
CFLAGS += -D $(ENABLED_RNG_MACRO)
CFLAGS += -std=c99 -Wall -O2
CFLAGS += -I $(TCL_HOME)/include

ifeq ($(UTS_PIC),1)
  CFLAGS += -fPIC
endif

AR = ar
ARFLAGS = rcs

all: $(LIB)

$(LIB): $(OBJS)
	rm -f $(@) # Remove old archive entries
	$(AR) $(ARFLAGS) $(@) $(^)

$(LIB_SO): $(OBJS)
	$(CC) -shared -Wl,-rpath,${TCL_HOME}/lib -o $(@) $(^) -L$(TCL_HOME)/lib -ltcl8.6

%.o: %.c uts.h uts_inline.h $(RNG_HEADERS)
	$(CC) $(CFLAGS) -c $(<) -o $(@)

clean:
	rm -f $(LIB) $(OBJS)
