
DIR := tests

TEST_SRC_C :=
TEST_SRC_C += $(DIR)/debug.c
TEST_SRC_C += $(DIR)/lib-turbine.c # issue #636
TEST_SRC_C += $(DIR)/ptasks-1.c
# This takes too long for ordinary testing:
# TEST_SRC_C += $(DIR)/ptasks-bench.c
# The stress test can fail: (#577)
# TEST_SRC_C += $(DIR)/ptasks-stress.c

# TEST_SRC_C += $(DIR)/checkpoint-1.c # hangs

ifeq ($(USE_MPE),1)
	TEST_SRC_C += $(DIR)/mpe-1.c \
                      $(DIR)/mpe-2.c
endif

TEST_SRC_C += $(DIR)/mpi-io-1.c
TEST_SRC_C += $(DIR)/mpi-io-2.c

TEST_SRC_TCL := $(DIR)/error.tcl               \
                $(DIR)/adlb-noop.tcl           \
	        $(DIR)/adlb-exists.tcl         \
	        $(DIR)/adlb-garbage-collect1.tcl \
	        $(DIR)/adlb-garbage-collect2.tcl \
	        $(DIR)/adlb-gc-svr-svr.tcl     \
	        $(DIR)/adlb-get-fail.tcl       \
	        $(DIR)/adlb-lookup-fail.tcl    \
	        $(DIR)/adlb-type-error.tcl     \
                $(DIR)/adlb-containers.tcl     \
                $(DIR)/adlb-multiset.tcl       \
                $(DIR)/noop.tcl                \
                $(DIR)/adlb-putget.tcl         \
                $(DIR)/batcher.tcl             \
                $(DIR)/adlb-data.tcl           \
                $(DIR)/adlb-lock1.tcl          \
                $(DIR)/adlb-lock2.tcl          \
                $(DIR)/adlb-iget.tcl           \
                $(DIR)/numbers.tcl             \
                $(DIR)/strings.tcl             \
                $(DIR)/float1.tcl              \
                $(DIR)/dht.tcl                 \
                $(DIR)/container2.tcl          \
                $(DIR)/container_load.tcl      \
                $(DIR)/container-enumerate.tcl \
                $(DIR)/container-close-1.tcl   \
                $(DIR)/container-close-2.tcl   \
                $(DIR)/container-serialize.tcl \
                $(DIR)/container-sum.tcl       \
                $(DIR)/container-sum2.tcl      \
                $(DIR)/string_split.tcl        \
                $(DIR)/enumerate-1.tcl         \
                $(DIR)/enumerate-2.tcl         \
                $(DIR)/dereference.tcl         \
                $(DIR)/reference.tcl           \
                $(DIR)/2Darray-1.tcl           \
                $(DIR)/2Darray-2.tcl           \
                $(DIR)/printf.tcl              \
                $(DIR)/blob1.tcl               \
                $(DIR)/blob2.tcl               \
                $(DIR)/blob3.tcl               \
                $(DIR)/argv.tcl                \
                $(DIR)/unpack.tcl              \
                $(DIR)/send_rule.tcl           \
                $(DIR)/deep_rule.tcl           \
                $(DIR)/deep_rule-2.tcl         \
                $(DIR)/subscript_rule.tcl      \
                $(DIR)/noop-exec-1.tcl      \
                $(DIR)/soft_target.tcl      \
                $(DIR)/sync-exec-1.tcl      \
                $(DIR)/data-placement-1.tcl    \
	        $(DIR)/adlb-globals.tcl     \
	        $(DIR)/turbine-globals.tcl     \

#                 $(DIR)/blob-big.tcl            \

ifeq ($(USE_MPE),1)
	TEST_SRC_TCL += $(DIR)/mpe-3.tcl
endif

ifeq ($(HAVE_COASTER),1)
	TEST_SRC_TCL += $(DIR)/coaster-exec-1.tcl
	TEST_SRC_TCL += $(DIR)/coaster-exec-2.tcl
	TEST_SRC_TCL += $(DIR)/coaster-exec-passive-1.tcl
endif

ifeq ($(ENABLE_STATIC_PKG),1)
	TEST_SRC_MANIFEST += $(DIR)/staticapp-1.manifest
	TEST_SRC_MANIFEST += $(DIR)/staticapp-bundled-1.manifest

	TEST_SRC_C += $(patsubst %.manifest, %.c, $(TEST_SRC_MANIFEST))
endif
