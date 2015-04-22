

DIR := src

SRCS += $(shell find $(DIR) -name "*.c" )
OBJS += $(patsubst %.c, %.o, $(SRCS))
DEPS += $(patsubst %.c, %.d, $(SRCS))

