Scripts to automatically move changes from svn to github

repo.sh: configuration of repo layout
checkout.sh: Checkout all repositories under current directories
pull.sh: Pull changes from both svn and github
rebase.sh: Apply changes from svn to github in a temporary branch
update-autotools.sh: Update autotools build scripts based on github
    version in a temporary branch
push.sh: Push changes from temporary branches to github, and update
    master repository with submodule changes
