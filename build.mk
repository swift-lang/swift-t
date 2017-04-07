
# BUILD.MK
# Use via build.sh

ASCIIDOC = asciidoc --attribute stylesheet=$(PWD)/swift.css \
                    --attribute max-width=800px

# Must compile leaf.txt with make-stc-docs.zsh (snippets, etc.)
all: guide.html gallery.html dev.html sites.html

define ASCIIDOC_CMDS
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)
endef

%.html: %.txt build.mk
	$(ASCIIDOC_CMDS)

guide.html: guide.txt
	$(ASCIIDOC_CMDS)
	./google-analytics.sh $(@)

TEXCV = $(HOME)/mcs/vita/texcv
BIB = $(TEXCV)/Biblio/Wozniak.bib
BIBGEN = $(TEXCV)/bibgen/bibgen

swift-t.list.adoc: $(BIBGEN) $(BIB)
	$(BIBGEN) $(BIB) swift-t.list asciidoc $(@)
# 	Update timestamp Asciidoc puts at end of file:
	@ touch pubs.txt

pubs.html: swift-t.list.adoc

# Gallery has extra dependencies and uses M4 to assemble
GALLERY_SWIFT = $(shell find gallery -name "*.swift")
GALLERY_SH    = $(shell find gallery -name "*.sh")
GALLERY_CODE = $(GALLERY_SWIFT) $(GALLERY_SH)

# This file is an intermediate artifact
gallery.txt: code.m4 gallery.txt.m4
	@ echo M4 $(<)
	@ m4 $(^) > $(@)

gallery.html: gallery.txt $(GALLERY_CODE)
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)

DOC_M4   = ${TURBINE_HOME}/maint/doc.m4
DOC_SH   = ${TURBINE_HOME}/maint/doc.sh
BLOB_H   = ${TURBINE_HOME}/src/tcl/blob/blob.h
BLOB_TCL = ${TURBINE_HOME}/lib/blob.tcl

blob.h.txt: $(BLOB_H) $(DOC_M4) $(DOC_SH) build.mk build.sh
	@ echo DOC.M4 $(<)
	@ $(DOC_SH) $(BLOB_H) $(@)

blob.tcl.txt: $(BLOB_TCL) $(DOC_M4) $(DOC_SH) build.mk build.sh
	@ echo DOC.M4 $(<)
	@ tr '#' ' ' < $(BLOB_TCL) > blob-nohash.txt
	@ $(DOC_SH) blob-nohash.txt $(@)

blob.txt: blob-leadin.txt blob.h.txt blob.tcl.txt
	@ echo CAT $(@)
	@ cat $(^) > $(@)

blob.html: blob.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)

downloads.html: downloads.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)

clean:
	rm -fv gallery.txt blob-nohash.txt blob.*.txt
	rm -fv leaf.html swift.html
	rm -fv leaf__1.*
