startTime=`date +%Y%m%d -d "-1 days"`000000
endTime=`date +%Y%m%d`000000
baseDir=`dirname $0`/..
sh $baseDir/bin/estc.sh -dfile labeledDocs/${startTime:0:8}