
DIR := src

SRCS += $(shell find $(DIR) -name "*.c" ! -name "*-template.c")
OBJS += $(patsubst %.c, %.o, $(SRCS))
DEPS += $(patsubst %.c, %.d, $(SRCS))
