baseDir=`dirname $0`/..
JAR=$baseDir/target/jgibblda-jar-with-dependencies.jar
MAIN=com.elex.bigdata.jgibblda.ResultEtl
echo "java -cp $JAR $MAIN $1"
java -cp $JAR $MAIN $1
