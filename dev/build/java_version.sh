
# JAVA VERSION SH
# The quoting in this function messes up Emacs,
# moving here for easier editing.

# Extract major version from java/javac

get_java_major_version()
{
  local CMD=$1
  local RESULT

  # We use 'sed -E' (POSIX extended regexp) rather than 'grep -oP'
  # (Perl mode): -P is a GNU extension and is not available in the BSD
  # grep shipped on macOS, where it fails with "grep: invalid option -- P".
  # sed -E works on both GNU and BSD.
  #
  # Pattern: match "version", optional whitespace, an optional quote
  # (" or '), then capture the leading run of digits (the major version),
  # discarding the rest of the line.
  #
  # So it extracts just the major version number from outputs like:
  # - java version "21.0.10"              → 21
  # - openjdk version "21.0.10-internal"  → 21
  # - javac version 21                    → 21

  # This first pattern works for java and javac for older versions
  # but javac seems to have changed for 21.0.12.1 2026-10-02
  RESULT=$( $CMD -version 2>&1 | \
            sed -E -n 's/.*version[[:space:]]+["'"'"']?([0-9]+).*/\1/p' | head -1 )
  if [[ $RESULT == "" ]]
  then
    # Newer pattern: 2026-10-02
    # Example:
    # $ javac -version
    # javac 21.0.12.1
    RESULT=$( $CMD -version 2>&1 | \
              sed -E -n "s/.*$CMD[[:space:]]+([0-9]+).*/\1/p" | head -1 )
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
