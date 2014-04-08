startTime=`date +%Y%m%d%H%M%S -d "-10 mins"`
endTime=`date +%Y%m%d%H%M%S` -d "-5 mins"`
baseDir=`dirname $0`/..
echo "sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime"
sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime