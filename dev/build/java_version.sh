
# JAVA VERSION SH
# The quoting in this function messes up Emacs,
# moving here for easier editing.

# Extract major version from java/javac

get_java_major_version()
{
  local CMD=$1
  local RESULT

  # GREP CHEAT SHEET
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

  # This first pattern works for java and javac for older versions
  # but javac seems to have changed for 21.0.12.1 2026-10-02
  RESULT=$( $CMD -version 2>&1 | \
            grep -oP "version [\"\']?\K[0-9]+" | head -1 )
  if [[ $RESULT == "" ]]
  then
    # Newer pattern: 2026-10-02
    # Example:
    # $ javac -version
    # javac 21.0.12.1
    RESULT=$( $CMD -version | \
              grep -oP "$CMD \K[0-9]+" | head -1 )
  fi
  if [[ $RESULT == "" ]]
  then
    # If we still haven't found it, write the text to stderr
    {
      echo "java_version.sh: could not find version for tool: $CMD"
      echo "java_version.sh: output from: $CMD -version"
      $CMD -version
      echo "java_version.sh: returning empty string."
    } >&2
  fi

  # Send result back to programmatic caller
  echo $RESULT
}
