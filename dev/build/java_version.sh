
# JAVA VERSION SH
# The quoting in this function messes up Emacs,
# moving here for easier editing.

# Extract major version from java/javac
get_java_major_version()
{
  local CMD=$1
  $CMD -version 2>&1 | grep -oP 'version [\"\']?\K[0-9]+' | head -1
}
