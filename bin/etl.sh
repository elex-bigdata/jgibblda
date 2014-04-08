args=$*
baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.ResultEtl
echo "java -cp $JAR $MAIN -modelDir /data/user_category/llda/models "
java -cp $JAR $MAIN -modelDir /data/user_category/llda/models $args
