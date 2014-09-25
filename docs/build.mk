
# BUILD.MK
# Use build.sh

ASCIIDOC = asciidoc --attribute stylesheet=$(PWD)/swift.css \
                    -a max-width=750px -a textwidth=80

# Must compile leaf.txt with make-stc-docs.zsh (snippets, etc.)
all: guide.html internals.html

%.html: %.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)
