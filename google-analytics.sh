#!/bin/sh

# Use sed to paste the Google Analytics HTML code
# right before the </body>

HTML=$1

sed -i '/<\/body>/ {
x
r google-analytics.html
}' $HTML
