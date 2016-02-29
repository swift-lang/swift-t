
import blob;
import io;

(blob sum) b(blob v) "b" "0.0"
[ "set <<sum>> [ b::b_tcl <<v>> ]" ];

file data = input_file("input.data");
blob v = blob_read(data);
blob s = b(v);
float sum[] = floats_from_blob(s);
printf("sum (swift): %f", sum[0]);
