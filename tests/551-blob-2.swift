#include <builtins.swift>
#include <blob.swift>
#include <assert.swift>

main {
    string s = "12345";
    blob b = blob_from_string(s);
    assertEqual(blob_size(b), 6, "blob_size");
    assertEqual(blob_size(b) + 1, 7, "blob_size + 1");
}
