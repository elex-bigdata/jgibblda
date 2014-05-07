package com.elex.bigdata.jgibblda;

import com.elex.bigdata.hashing.BDMD5;
import com.elex.bigdata.hashing.HashingException;
import com.elex.bigdata.ro.BasicRedisShardedPoolManager;
import com.elex.bigdata.util.MetricMapping;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import redis.clients.jedis.ShardedJedis;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Created with IntelliJ IDEA.
 * User: yb
 * Date: 3/27/14
 * Time: 6:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class ResultEtl {
  private BasicRedisShardedPoolManager manager = new BasicRedisShardedPoolManager("jgibblda", "/redis.site.properties");
  private Map<String, String> uidCategories = new HashMap<String, String>();
  private static Logger logger = Logger.getLogger(ResultEtl.class);

  public static void main(String[] args) throws IOException, HashingException {
    LDACmdOption option = new LDACmdOption();
    CmdLineParser parser = new CmdLineParser(option);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    String baseDir=option.modelDir;
    String dfile=option.dfile;
    List<String> projects = new ArrayList<String>();
    if (option.project.equals("")) {
      //todo
      //get all projects to add to projects list
      for (String project : MetricMapping.getInstance().getAllProjectShortNameMapping().keySet())
        projects.add(project);
    } else {
      projects.add(option.project);
    }
    for (String project : projects) {
      try{
      String fileName = baseDir+File.separator+project+File.separator + dfile;
      logger.info("load result from " + fileName);
      ResultEtl resultEtl = new ResultEtl();
      resultEtl.loadResult(fileName);
      logger.info("load Result completely. " + " users " + resultEtl.uidCategories.size());
      resultEtl.putToRedis();
      }catch (FileNotFoundException e){
        e.printStackTrace();
        continue;
      }
    }
  }

  public void loadResult(String tthetafile) throws IOException, HashingException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(
      new GZIPInputStream(
        new FileInputStream(tthetafile)), "UTF-8"));
    //todo get resultFile according to date and tthetafile
    File tthetaFile=new File(tthetafile);
    //String project=new File(tthetaFile.getParent()).getName();
    String modelRootPath=new File(tthetaFile.getParent()).getParent();
    String rootPath=new File(modelRootPath).getParent();
    String resultRootPath=rootPath+File.separator+"results";
    Date date=new Date();
    int minutes=date.getMinutes()+date.getHours()*60;
    int index=minutes/5;
    String resultFilePath=resultRootPath+File.separator+date.getDate()+"."+index;
    File resultFile=new File(resultFilePath);
    //if resultFile exists and last modified earlier than 1 day ago.
    if(resultFile.exists()&&(resultFile.lastModified()<System.currentTimeMillis()-24*60*60*1000))
    {
      resultFile.delete();
    }
    //open writer with append mode.
    BufferedWriter writer=new BufferedWriter(new FileWriter(resultFilePath,true));
    String line = null;
    logger.info("start to read file "+tthetafile);
    while ((line = reader.readLine()) != null) {
      List<Integer> probabilities = new ArrayList<Integer>();
      String[] uidThetas = line.split("\t");
      String uid = uidThetas[0];
      String Thetas = uidThetas[1];
      String[] labelProbabilities = Thetas.split(" ");
      if(labelProbabilities.length<2)
        continue;
      Double aProb=Double.parseDouble(labelProbabilities[0].split(":")[1]);
      Double bProb=Double.parseDouble(labelProbabilities[1].split(":")[1]);
      if(aProb>bProb&&aProb<0.51){
        probabilities.add((int)(aProb*100)+1);
        probabilities.add((int)(bProb*100));
      }else if(bProb>aProb&&bProb<0.51){
        probabilities.add((int)(aProb*100));
        probabilities.add((int)(bProb*100)+1);
      }else {
        probabilities.add((int)(aProb*100));
        probabilities.add((int)(bProb*100));
      }
      StringBuilder probBuilder = new StringBuilder();
      probBuilder.append("a");
      if (probabilities.get(0) < 16)
        probBuilder.append("0");
      probBuilder.append(Integer.toHexString(probabilities.get(0)));
      probBuilder.append("b");
      if (probabilities.get(1) < 16)
        probBuilder.append("0");
      probBuilder.append(Integer.toHexString(probabilities.get(1)));
      probBuilder.append("z");
      if (100 - probabilities.get(0) - probabilities.get(1) < 16)
        probBuilder.append("0");
      probBuilder.append(Integer.toHexString(100 - probabilities.get(0) - probabilities.get(1)));
      String uidMd5= BDMD5.getInstance().toMD5(uid);
      String probStr=probBuilder.toString();
      uidCategories.put(uidMd5, probStr);
      //logger.info(uid + "\t" + probStr);
      writer.write(uid+"\t"+ probStr);
      writer.newLine();
    }
    logger.info("read file "+tthetafile +" completely");
    writer.flush();
    writer.close();
    reader.close();
  }

  public void putToRedis() {
    ShardedJedis shardedJedis = null;
    boolean successful = true;
    try {
      shardedJedis = manager.borrowShardedJedis();
      logger.info("put to redis "+uidCategories.size()+" entries");

      for (Map.Entry<String, String> entry : uidCategories.entrySet()) {
        shardedJedis.set(entry.getKey(), entry.getValue());
      }
    } catch (Exception e) {
      e.printStackTrace();
      successful = false;
    } finally {
      if (successful){
        manager.returnShardedJedis(shardedJedis);
        logger.info("put To redis Success");
      }
      else {
        manager.returnBrokenShardedJedis(shardedJedis);
        logger.info("put To redis failed");
      }
    }
  }
}
