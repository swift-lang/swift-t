
# APPS MODULE MK

BATCHER = $(INSTALL_BIN)/adlb-batcher

install-batcher: $(BATCHER)

$(BATCHER): apps/batcher.x
	mkdir -pv $(INSTALL_BIN)
	cp -uv $(<) $(@)

LINK = -L $(INSTALL_LIB) -l adlb -Wl,-rpath,$(INSTALL_LIB)

apps/batcher.x: install apps/batcher.o
	$(CC) -o $(@) apps/batcher.o $(LINK)

.PHONY: install-batcher
