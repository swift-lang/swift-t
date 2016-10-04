
DEB_LIST = maint/debian-list.mk

include maint/version.mk
include $(DEB_LIST)

STC_DEB = stc_$(VERSION)_amd64.deb

deb: $(STC_DEB)

# STC has only one Debian type, with headers, but this is still
# called a bin
DEBIAN_PKG_TYPE = bin
UPSTREAM_TGZ = stc_$(VERSION).orig.tar.gz

DEB_FILE_PATHS = $(wildcard maint/debian/*)

FILE_LIST = maint/file-list.zsh

# Just for TGZ dependency
DEBIAN_STUFF = $(FILE_LIST) $(DEB_LIST) $(DEB_FILE_PATHS) \
		maint/debian.mk

$(UPSTREAM_TGZ): $(DEBIAN_STUFF) configure Makefile build.xml
	../../dev/debian/mk-upstream-tgz.sh ${DEBIAN_PKG_TYPE} \
		$(@) stc $(VERSION) $(FILE_LIST)

$(STC_DEB): $(UPSTREAM_TGZ)
	../../dev/debian/mk-debian.zsh ${DEBIAN_PKG_TYPE} $(@) $(<) \
		stc $(VERSION)

clean:: clean-deb

clean-deb::
	@echo "CLEAN DEBIAN"
	@rm -fv *.deb *.orig.tar.gz
	@rm -rf deb-work-*
