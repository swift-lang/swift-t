
# BUILD.MK
# Use build.sh

ASCIIDOC = asciidoc --attribute stylesheet=$(PWD)/swift.css \
                    -a max-width=750px -a textwidth=80

# Must compile leaf.txt with make-stc-docs.zsh (snippets, etc.)
all: guide.html gallery.html dev.html sites.html

%.html: %.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)

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

downloads.html: downloads.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)

clean:
	rm -fv gallery.txt
	rm -fv leaf.html swift.html
	rm -fv leaf__1.*
