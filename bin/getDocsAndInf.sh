#get url access log from startTime to endTime and merge them to today's log and then inf them all
startTime=$1
endTime=$2
baseDir=`dirname $0`/..
echo "sh $baseDir/../Url_Count/bin/getDocs.sh $startTime $endTime"
sh $baseDir/../Url_Count/bin/getDocs.sh $startTime $endTime
echo "sh $baseDir/../jgibblda/bin/inf.sh -dfile ${startTime:0:8} -niters 600 -nburnin 400"
sh $baseDir/../jgibblda/bin/inf.sh -dfile ${startTime:0:8} -niters 600 -nburnin 400