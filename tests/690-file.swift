import assert;
import io;

// Check that we can extract filename from file

(string output)
echo_filename(file input) "funcs" "0.0"
[ "set <<output>> [ turbine::local_file_path <<input>> ]" ];

main {
  file f = input_file("alice.txt");
  string s = echo_filename(f);
  printf("filename: %s", s);
  assertEqual(s, "alice.txt", "Filename correct");
}
