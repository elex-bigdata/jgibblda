startTime=`date +%Y%m%d%H -d "-1 hours"`0000
endTime=`date +%Y%m%d%H`0000
baseDir=`dirname $0`/..
echo "sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime"
sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime