#!/bin/bash
j=0
i=1
while [ $i -le 3 ]; do
	j=$((12000+i))
    screen -S $i -d -m java -jar tutorial.jar $i $j &
    i=$((i+1))
done        
     
