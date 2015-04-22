#Create file that test expects

echo "HELLO" > tmp1.txt
echo "BYE" > tmp2.txt
tar cvvzf test_675.tgz tmp1.txt tmp2.txt
rm tmp1.txt tmp2.txt
