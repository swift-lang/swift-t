Scripts to automatically move changes from svn to github

THESE ARE NOW OBSOLETE SINCE WE HAVE MIGRATED TO DEVELOPING ON GITHUB

Files:
--------
checkout.sh: Checkout all repositories under current directories
pull.sh: Pull changes from both svn and github
update.sh: Fetch changes from both repositories and apply svn changes
           to master branch.
rebase.sh: Create temporary branches and apply changes from svn to github.
push.sh: Push changes from temporary branches to github, and update
         master repository with submodule changes
update-autotools.sh: Update autotools build scripts based on github
                     version in a temporary branch
repos.sh: configuration file for repo layout

Use Cases:
---------
Checking out a fresh copy into a swift-t directory:

  mkdir swift-t
  cd swift-t
  /path/to/checkout.sh

Update github with latest changes from svn repo:

  /path/to/update.sh
  /path/to/rebase.sh
  /path/to/push.sh

Rebuild autotools configuration scripts and push to github:
  
  /path/to/update-autotools.sh
  /path/to/push.sh
