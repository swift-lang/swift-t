// Test compile-time args
import assert;
import sys;

argv_accept("compile_arg", "runtime-arg");
assertEqual("SUCCESS!", argv("compile_arg"), "Test compile time arg");
assertEqual("SUCCESS", argv("runtime-arg"), "Test runtime arg");
