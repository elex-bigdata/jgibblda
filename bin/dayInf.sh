startTime=`date +%Y%m%d -d "-1 days"`000000
endTime=`date +%Y%m%d`000000
baseDir=`dirname $0`/..
echo "sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime"
sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime