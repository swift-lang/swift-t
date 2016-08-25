
DEB_LIST = maint/debian-list.mk

include maint/version.mk
include $(DEB_LIST)

EXMCUTILS_DEB     = exmcutils_$(VERSION)-1_amd64.deb
EXMCUTILS_DEV_DEB = exmcutils-dev_$(VERSION)-1_amd64.deb

deb: $(EXMCUTILS_DEB)
dev-deb: $(EXMCUTILS_DEV_DEB)

ifeq (,$(filter dev-deb,$(MAKECMDGOALS)))
  # Make binary package (no headers)
  DEBIAN_PKG_TYPE = bin
  UPSTREAM_TGZ = exmcutils_$(VERSION).orig.tar.gz
else
  # Make dev package (with headers)
  DEBIAN_PKG_TYPE = dev
  UPSTREAM_TGZ = exmcutils-dev_$(VERSION).orig.tar.gz
endif

DEB_FILE_PATHS = $(patsubst %,debian/%,$(DEB_FILES))

FILE_LIST = maint/file-list.zsh

$(UPSTREAM_TGZ): $(FILE_LIST) $(DEB_LIST) $(DEB_FILE_PATHS) configure
	../../dev/debian/mk-upstream-tgz.sh ${DEBIAN_PKG_TYPE} \
		$(@) exmcutils $(VERSION) $(FILE_LIST)

$(EXMCUTILS_DEB) $(EXMCUTILS_DEV_DEB): $(UPSTREAM_TGZ)
	maint/mk-debian.zsh ${DEBIAN_PKG_TYPE} $(@) $(<) exmcutils $(VERSION)

clean:: clean-deb

clean-deb::
	$(Q) "  CLEAN DEBIAN"
	$(E) rm -fv debian *.deb *.orig.tar.gz
