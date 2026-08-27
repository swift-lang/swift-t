
# JAVA VERSION SH
# The quoting in this function messes up Emacs,
# moving here for easier editing.

# Extract major version from java/javac

get_java_major_version()
{
  local CMD=$1

  # -oP      Output only matching text in Perl-compatible regex mode
  # version  Match the literal word "version" followed by a space
  # [\"\']?  Match an optional quote character
  #          (either double " or single '), zero or one times
  # \K       Keep assertion: discard everything matched
  #          before this point from the output
  # [0-9]+   Match one or more digits but not dot

  # So it extracts just the major version number from outputs like:
  # - java version "21.0.10" → 21
  # - javac version 21 → 21
  # - openjdk version "21.0.10-internal" → 21

  # The \K is key: it lets us match the "version" prefix
  #                without including it in the output.

  $CMD -version 2>&1 | grep -oP "version [\"\']?\K[0-9]+" | head -1
}
