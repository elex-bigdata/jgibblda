startTime=`date +%Y%m%d%H%M%S -d "-8 mins"`
endTime=`date +%Y%m%d%H%M%S` -d "-3 mins"`
baseDir=`dirname $0`/..
echo "sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime"
sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime