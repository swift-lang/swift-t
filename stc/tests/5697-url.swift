// We should be able to write a function that does something like copy a URL
import assert;

app (url o) copy_url(url i)
{
  // Simulate copying a url
  "echo" "copy_url" i o;
}

(url o) copy_url2 (url i) {
    o = copy_url(i);
}

(url o) copy_url3(url i) "turbine" "0.0" [
  "puts [ list copy_url3 [ turbine::local_file_path <<i>> ] [ turbine::local_file_path $<<o>> ] ]"
];

(url o) copy_url4 (url i) {
    o = copy_url3(i);
}

(url o) copy_url5(url i) "funcs_5697" "0.5" "copy_url5" [
  """
  funcs_5697::copy_url5_impl [ turbine::local_file_path <<i>> ] [ turbine::local_file_path $<<o>> ]
  """
];

(url o) copy_url6 (url i) {
    o = copy_url5(i);
}

main() {
  // This should be valid
  url f<"ftp://example.com/"> = copy_url(input_url("http://example.com"));

  assertEqual(urlname(f), "ftp://example.com/", "url");

  // Check that we can pass url as swift function output
  url f2<"ftp://example.com/two"> = copy_url2(input_url("http://example.com"));

  assertEqual(urlname(f2), "ftp://example.com/two", "url 2");

  url f3<"file://test3"> = copy_url3(input_url("http://three.com"));

  assertEqual(urlname(f3), "file://test3", "url 3");
  
  url f4<"file://test4"> = copy_url4(input_url("http://four.com"));

  assertEqual(urlname(f4), "file://test4", "url 4");
 
  url f5<"file://test5"> = copy_url5(input_url("http://five.com"));

  assertEqual(urlname(f5), "file://test5", "url 5");
 
  url f6<"file://test6"> = copy_url6(input_url("http://six.com"));

  assertEqual(urlname(f6), "file://test6", "url 6");
}

