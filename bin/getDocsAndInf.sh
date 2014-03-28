//get url access log from startTime to endTime and merge them to today's log and then inf them all
startTime=$1
endTime=$2
sh ../../../Url_Count/bin/getDocs.sh $startTime $endTime
sh ../../../jgibblda/bin/inf.sh -dfile labeledDocs/${endTime:0:8}
sh ../../../jgibblda/bin/etl.sh ${endTime:0:8}.gz