
DEB_LIST = maint/debian-list.mk

include maint/version.mk
include $(DEB_LIST)

ADLBX_DEB     = adlbx_$(VERSION)-1_amd64.deb
ADLBX_DEV_DEB = adlbx-dev_$(VERSION)-1_amd64.deb

deb: $(ADLBX_DEB)
dev-deb: $(ADLBX_DEV_DEB)

ifeq (,$(filter dev-deb,$(MAKECMDGOALS)))
  # Make binary package (no headers)
  DEBIAN_PKG_TYPE = bin
  UPSTREAM_TGZ = adlbx_$(VERSION).orig.tar.gz
else
  # Make dev package (with headers)
  DEBIAN_PKG_TYPE = dev
  UPSTREAM_TGZ = adlbx-dev_$(VERSION).orig.tar.gz
endif

DEB_FILE_PATHS = $(wildcard maint/debian-dev/* maint/debian-bin/*)

FILE_LIST = maint/file-list.zsh

# Just for TGZ dependency
DEBIAN_STUFF = $(FILE_LIST) $(DEB_LIST) $(DEB_FILE_PATHS) \
		maint/debian.mk

$(UPSTREAM_TGZ): $(DEBIAN_STUFF) configure Makefile
	../../dev/debian/mk-upstream-tgz.sh ${DEBIAN_PKG_TYPE} \
		$(@) adlbx 	$(VERSION) $(FILE_LIST)

$(ADLBX_DEB) $(ADLBX_DEV_DEB): $(UPSTREAM_TGZ)
	../../dev/debian/mk-debian.zsh ${DEBIAN_PKG_TYPE} $(@) $(<) \
		adlbx $(VERSION)

clean:: clean-deb

clean-deb::
	$(Q) "  CLEAN DEBIAN"
# 	This may be a soft link (normal build) or a directory (mk-debian)
	$(E) rm -rfv debian
	$(E) rm -fv *.deb *.orig.tar.gz
	$(E) rm -rf deb-work-*
