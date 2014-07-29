
ASCIIDOC = asciidoc --attribute stylesheet=$(PWD)/swift.css \
                    -a max-width=750px -a textwidth=80

all: guide.html internals.html

%.html: %.txt
	@ echo ASCIIDOC $(<)
	@ $(ASCIIDOC) $(<)
