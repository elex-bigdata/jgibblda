startTime=`date +%Y%m%d%H%M%S -d "-10 mins"`
endTime=`date +%Y%m%d%H%M%S -d "-5 mins"`
baseDir=`dirname $0`/..
logFile=/data/user_category/llda/logs/fiveMinInf.log
echo "sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime"
echo "`date` sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime" >> $logFile
sh $baseDir/bin/getDocsAndInf.sh $startTime $endTime
echo "end time `date`" >> $logFile