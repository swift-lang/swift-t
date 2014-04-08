
RNG_HEADERS = $(shell find rng -name "*.h")
RNG_SRC = $(shell find rng -name "*.c")
RNG_OBJS = $(patsubst %.c, %.o, $(RNG_SRC))

OBJS = $(RNG_OBJS) uts.o uts_leaf.o
LIB = libuts.a

CC = gcc
CFLAGS += -D BRG_RNG
CFLAGS += -std=c99

AR = ar
ARFLAGS = rcs

all: $(LIB)

$(LIB): $(OBJS)
	$(AR) $(ARFLAGS) $(@) $(^)

%.o: %.c uts.h $(RNG_HEADERS)
	$(CC) $(CFLAGS) -c $(<) -o $(@)
