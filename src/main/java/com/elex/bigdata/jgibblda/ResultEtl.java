package com.elex.bigdata.jgibblda;

import com.elex.bigdata.ro.BasicRedisShardedPoolManager;
import org.apache.log4j.Logger;
import redis.clients.jedis.ShardedJedis;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  private static Logger logger=Logger.getLogger(ResultEtl.class);
  public static void main(String[] args) throws IOException {
    String fileName="/data/log/user_category/llda/"+args[0];
    logger.info("load result from " +fileName);
    ResultEtl resultEtl=new ResultEtl();
    resultEtl.loadResult(fileName);
    logger.info("load Result completely. "+" users "+resultEtl.uidCategories.size());
    resultEtl.putToRedis();
  }

  private void loadResult(String tthetafile) throws IOException {
    BufferedReader reader = new BufferedReader(new InputStreamReader(
      new GZIPInputStream(
        new FileInputStream(tthetafile)), "UTF-8"));
    String line = null;
    while ((line = reader.readLine()) != null) {
      List<Integer> probabilities = new ArrayList<Integer>();
      String[] uidThetas = line.split("\t");
      String uid = uidThetas[0];
      String Thetas = uidThetas[1];
      String[] labelProbabilities = Thetas.split(" ");
      for (String labelProbality : labelProbabilities) {
        probabilities.add((int) (Double.parseDouble(labelProbality.split(":")[1]) * 100));
      }
      StringBuilder probBuilder = new StringBuilder();
      probBuilder.append("a" + Integer.toHexString(probabilities.get(0)));
      probBuilder.append("b" + Integer.toHexString(probabilities.get(1)));
      probBuilder.append("z" + Integer.toHexString(100 - probabilities.get(0) - probabilities.get(1)));

      uidCategories.put(uid, probBuilder.toString());
      logger.debug(uid+"\t"+probBuilder.toString());
    }
  }

  private void putToRedis() {
    ShardedJedis shardedJedis = null;
    boolean successful = true;
    try {
      shardedJedis = manager.borrowShardedJedis();

      for (Map.Entry<String, String> entry : uidCategories.entrySet()) {
        shardedJedis.set(entry.getKey(), entry.getValue());
      }
    } catch (Exception e) {
      e.printStackTrace();
      successful = false;
    } finally {
      if (successful)
        manager.returnShardedJedis(shardedJedis);
      else{
        manager.returnBrokenShardedJedis(shardedJedis);
        logger.info("put To redis failed");
      }
    }
  }
}
