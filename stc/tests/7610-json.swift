
import files;
import json;
import string;

fn = "7610-data.json";
trace(json_type(trim(read(input(fn))), "main"));
trace(json_array_size(read(input(fn)), "main"));
trace(json_get(read(input(fn)), "main"));
trace(json_object_names(read(input(fn)), "keys"));
trace(json_get(read(input(fn)), "d1,d2"));
