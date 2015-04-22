while read a b
do
  echo $a $b
  sed -e "s/nodes=[0-9]*/nodes=$a/" \
      -e "s/WAVEFRONT_N=[0-9]\+/WAVEFRONT_N=$b/" \
      ./bw-aprun.sh > ./bw-aprun.n$a.sh
  
done < params.txt
