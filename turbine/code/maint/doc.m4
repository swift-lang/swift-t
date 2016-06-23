dnl See doc.sh for usage
m4_divert(`-1')
m4_define(`DOCT', `m4_divert(0)m4_patsubst(`$1',`^ *',`') m4_divert(`-1')' )
m4_define(`DOCN', `m4_divert(0)m4_patsubst(`$1',`^ *',`') 
m4_divert(`-1')' )
m4_define(`DOCNN', `m4_divert(0)m4_patsubst(`$1',`^ *',`') 

m4_divert(`-1')' )
m4_define(`DOCD', `m4_divert(0)
+$1+:: $2

m4_divert(`-1')' )
m4_define(`DOC_CODE', `m4_divert(0)
[listing]
$1

m4_divert(`-1')' )
