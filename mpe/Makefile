
# MAKEFILE: MPE TOOLS

# Note that the library, mpe.c, is simply #include'd
# by all the programs

CC = gcc

CFLAGS = -O0 -g -std=gnu99 -Wall

EXMCUTILS = $(HOME)/research/projects/ExM/c-utils

INCLUDES = -I $(EXMCUTILS)/include
LIBS     = -L $(EXMCUTILS)/lib -l exmcutils
LIBS     += -Wl,-rpath -Wl,$(EXMCUTILS)/lib

CFLAGS += $(INCLUDES)

PROGRAMS= decode.x      \
	  first-last.x	\
	  list-events.x \
          load.x        \
          profile.x     \
          time-counts.x

all: $(PROGRAMS)

%.x: %.o mpe.c
	gcc $(<) -o $(@) $(LIBS)

clean:
	rm -fv *.o *.x
