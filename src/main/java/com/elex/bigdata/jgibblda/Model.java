/*
 * Copyright (C) 2007 by
 * 
 * 	Xuan-Hieu Phan
 *	hieuxuan@ecei.tohoku.ac.jp or pxhieu@gmail.com
 * 	Graduate School of Information Sciences
 * 	Tohoku University
 * 
 *  Cam-Tu Nguyen
 *  ncamtu@gmail.com
 *  College of Technology
 *  Vietnam National University, Hanoi
 *
 * JGibbsLDA is a free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation; either version 2 of the License,
 * or (at your option) any later version.
 *
 * JGibbsLDA is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JGibbsLDA; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 */
package com.elex.bigdata.jgibblda;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.TIntHashSet;

public class Model {

  //---------------------------------------------------------------
  //	Class Variables
  //---------------------------------------------------------------

  private static String tassignSuffix = ".tassign.gz";   // suffix for topic assignment file
  private static String thetaSuffix = ".theta.gz";    // suffix for theta (topic - document distribution) file
  private static String phiSuffix = ".phi.gz";      // suffix for phi file (topic - word distribution) file
  private static String othersSuffix = ".others.gz";   // suffix for containing other parameters
  private static String twordsSuffix = ".twords.gz";   // suffix for file containing words-per-topics
  private static String wordMapSuffix = ".wordmap.gz"; // suffix for file containing word to id map

  //---------------------------------------------------------------
  //	Model Parameters and Variables
  //---------------------------------------------------------------


  private String dir = "./";
  private String modelDir= "./";
  private String docDir= "./";
  private String dfile = "trndocs.dat";
  private boolean unlabeled = false;
  private String modelName = "model";
  private LDADataset data; // link to a dataset
  private LDADataset currentLoadedData; //when estimate from last estimate clonedData contains the new documents.

  private int M = 0;          // dataset size (i.e., number of docs)
  private int V = 0;          // vocabulary size
  private int K = 100;        // number of topics
  private double alpha;       // LDA hyperparameters
  private double beta = 0.01; // LDA hyperparameters
  private int niters = 1000;  // number of Gibbs sampling iteration
  private int nburnin = 500;  // number of Gibbs sampling burn-in iterations
  private int samplingLag = 5;// Gibbs sampling sample lag
  private int numSamples = 1; // number of samples taken
  private int liter = 0;      // the iteration at which the model was saved
  private int twords = 20;    // print out top words per each topic

  // Estimated/Inferenced parameters
  private double[][] theta = null; // theta: document - topic distributions, size M x K
  private double[][] phi = null;   // phi: topic-word distributions, size K x V

  // Temp variables while sampling
  private TIntArrayList[] z = null; // topic assignments for words, size M x doc.size()
  private int[][] nw = null;       // nw[i][j]: number of instances of word/term i assigned to topic j, size V x K
  private int[][] nd = null;       // nd[i][j]: number of words in document i assigned to topic j, size M x K
  private int[] nwsum = null;      // nwsum[j]: total number of words assigned to topic j, size K
  private int[] ndsum = null;      // ndsum[i]: total number of words in document i, size M

  private ArrayList<TIntObjectHashMap<int[]>> nw_inf = null;       // nw[m][i][j]: number of instances of word/term i assigned to topic j in doc m, size M x V x K
  private int[][] nwsum_inf = null;      // nwsum[m][j]: total number of words assigned to topic j in doc m, size M x K

  // temp variables for sampling
  private double[] p = null;

  //---------------------------------------------------------------
  //	Constructors
  //---------------------------------------------------------------

  public Model(LDACmdOption option) throws FileNotFoundException, IOException {
    this(option, null);
  }

  public Model(LDACmdOption option, Model trnModel) throws FileNotFoundException, IOException {
    this(option, null,null,trnModel);
  }

  public Model(LDACmdOption option,String dDir,String mDir,Model trnModel) throws IOException {


    modelName = option.modelName;
    K = option.K;

    alpha = option.alpha;
    if (alpha < 0.0)
      alpha = 50.0 / K;

    if (option.beta >= 0)
      beta = option.beta;

    niters = option.niters;
    nburnin = option.nburnin;
    samplingLag = option.samplingLag;

    dir = option.dir;
    if (dir.endsWith(File.separator))
      dir = dir.substring(0, dir.length() - 1);
    if(mDir!=null)
      modelDir=mDir;
    else
      modelDir=option.modelDir;
    if (modelDir.endsWith(File.separator))
      modelDir = modelDir.substring(0, modelDir.length() - 1);
    if(dDir!=null)
      docDir=dDir;
    else
      docDir=option.docDir;
    if (docDir.endsWith(File.separator))
      docDir = docDir.substring(0, docDir.length() - 1);
    dfile = option.dfile;
    unlabeled = option.unlabeled;
    twords = option.twords;

    // initialize dataset
    data = new LDADataset();

    // process trnModel (if given)
    if (trnModel != null) {
      data.setDictionary(trnModel.data.getLocalDict());
      K = trnModel.K;

      // use hyperparameters from model (if not overridden in options)
      if (option.alpha < 0.0)
        alpha = trnModel.alpha;
      if (option.beta < 0.0)
        beta = trnModel.beta;
    }

    // read in data
    data.readDataSet(docDir + File.separator + dfile, unlabeled);
    File modelPath=new File(modelDir);
    if(!modelPath.exists())
      modelPath.mkdirs();

  }

  public Model(LDACmdOption option,String dDir,String mDir) throws IOException {
    this(option,dDir,mDir,null);
  }


  //---------------------------------------------------------------
  //	Init Methods
  //---------------------------------------------------------------

  /**
   * Init parameters for estimation or inference
   */
  public boolean init(boolean random, boolean est) throws IOException {
    //random estimate from raw data loaded from disk.(in the constructor function)
    if (random) {
      //data.readDataSet(dir + File.separator + dfile, unlabeled);
      M = data.getM();
      V = data.getV();
      z = new TIntArrayList[M];
    } else {
      //load model (alpha/beta/K/liter/V/M and documents and z(doc-word-topic);set data to model's data)
      if (!loadModel()) {
        System.out.println("Fail to load word-topic assignment file of the model!");
        return false;
      }
      //estimate or inference from last estimate
      if (est) {
        //estimate from last estimate;save data loaded from disk according to dfile
        currentLoadedData = new LDADataset();
        //set local dictionary as trained dictionary
        currentLoadedData.setLocalDict(data.getLocalDict());
        currentLoadedData.readDataSet(docDir + File.separator + dfile, unlabeled);
      }


      // debug output
      System.out.println("Model loaded:");
      System.out.println("\talpha:" + alpha);
      System.out.println("\tbeta:" + beta);
      System.out.println("\tK:" + K);
      System.out.println("\tM:" + M);
      System.out.println("\tV:" + V);
    }

    p = new double[K];
    //est from last estimation merge currentLoadedData to data
    if (est && !random) {
      mergeTrainData(currentLoadedData, data);
      M = data.getM();
      V = data.getV();
      TIntArrayList[] mergedZ = new TIntArrayList[data.getM()];
      for (int m = 0; m < M; m++) {
        mergedZ[m]=new TIntArrayList();
        for (int n = 0; n < data.getDocs().get(m).getLength(); n++) {
          int topic = (int) Math.floor(Math.random() * K);
          if (m < z.length && n<z[m].size())
            topic=z[m].get(n);
          mergedZ[m].add(topic);
        }
      }
      z=mergedZ;
    }
    //init nw(word(Cf)-topic),nd((word numbers in a doc)-topic),nwSum(words-topic),ndSum(words-doc)
    initSS();


    for (int m = 0; m < data.getM(); m++) {
      //estimate from raw data;init z.
      if (random) {
        z[m] = new TIntArrayList();
      }

      // initilize for z
      int N = data.getDocs().get(m).getLength();
      for (int n = 0; n < N; n++) {
        int w = data.getDocs().get(m).getWords()[n];
        int cf = data.getDocs().get(m).getCfs()[n];
        int topic;

        // random init a topic or load existing topic from z[m]
        if (random) {
          topic = (int) Math.floor(Math.random() * K);
          z[m].add(topic);
        } else {
          topic = z[m].get(n);
        }
        //compute nw,nd,nwsum,ndsum
        nw[w][topic] += cf; // number of instances of word assigned to topic j
        nd[m][topic] += cf; // number of words in document i assigned to topic j
        nwsum[topic] += cf; // total number of words assigned to topic j
        ndsum[m] += cf;
      }

//            ndsum[m] = N; // total number of words in document i
    }

    theta = new double[M][K];
    phi = new double[K][V];

    return true;
  }

  private void mergeTrainData(LDADataset currentLoadedData, LDADataset data) {
    currentLoadedData.mergeTrainedDocuments(data, true);
    Map<Integer, Integer> sameUidDocMap = new HashMap<Integer, Integer>();
    for (int i = 0; i < currentLoadedData.getDocs().size(); i++) {
      Integer trainedDocNum = data.getDocNum(currentLoadedData.getUid(i));
      if (trainedDocNum != null) {
        data.setDoc(currentLoadedData.getDocs().get(i), trainedDocNum);
        sameUidDocMap.put(i, trainedDocNum);
      }

    }
    int docNum = data.getDocs().size();
    for (int i = 0; i < currentLoadedData.getDocs().size(); i++) {
      if (!sameUidDocMap.containsKey(i)) {
        data.setDoc(currentLoadedData.getDocs().get(i), docNum);
        data.setUidDocNum(currentLoadedData.getUid(i), docNum++);
      }
    }
    data.setM(docNum);
    data.setLocalDict(currentLoadedData.getLocalDict());
    data.setV(currentLoadedData.getV());

  }


  public boolean initInf() {
    nw_inf = new ArrayList<TIntObjectHashMap<int[]>>();

    nwsum_inf = new int[M][K];
    for (int m = 0; m < M; m++) {
      for (int k = 0; k < K; k++) {
        nwsum_inf[m][k] = 0;
      }
    }

    for (int m = 0; m < data.getM(); m++) {
      nw_inf.add(m, new TIntObjectHashMap<int[]>());

      // initilize for z
      int N = data.getDocs().get(m).getLength();
      for (int n = 0; n < N; n++) {
        int w = data.getDocs().get(m).getWords()[n];
        int cfs = data.getDocs().get(m).getCfs()[n];

        int topic = z[m].get(n);

        if (!nw_inf.get(m).containsKey(w)) {
          int[] nw_inf_m_w = new int[K];
          for (int k = 0; k < K; k++) {
            nw_inf_m_w[k] = 0;
          }
          nw_inf.get(m).put(w, nw_inf_m_w);
        }

        nw_inf.get(m).get(w)[topic] += cfs; // number of instances of word assigned to topic j in doc m
        //nw_inf[m][w][topic]++; // number of instances of word assigned to topic j in doc m
        nwsum_inf[m][topic] += cfs; // total number of words assigned to topic j in doc m
      }
    }

    return true;
  }

  /**
   * Init sufficient stats
   */
  protected void initSS() {
    nw = new int[V][K];
    for (int w = 0; w < V; w++) {
      for (int k = 0; k < K; k++) {
        nw[w][k] = 0;
      }
    }

    nd = new int[M][K];
    for (int m = 0; m < M; m++) {
      for (int k = 0; k < K; k++) {
        nd[m][k] = 0;
      }
    }

    nwsum = new int[K];
    for (int k = 0; k < K; k++) {
      nwsum[k] = 0;
    }

    ndsum = new int[M];
    for (int m = 0; m < M; m++) {
      ndsum[m] = 0;
    }
  }

  /*
    get same uid-Documents from trainModule and merge them into data
   */
  public void mergeSameUidDocuments(Model trainModel) {

  }

  //---------------------------------------------------------------
  //	Update Methods
  //---------------------------------------------------------------

  public void updateParams() {
    updateTheta();
    updatePhi();
    numSamples++;
  }

  public void updateParams(Model trnModel) {
    updateTheta();
    updatePhi(trnModel);
    numSamples++;
  }

  public void updateTheta() {
    double Kalpha = K * alpha;
    for (int m = 0; m < M; m++) {
      for (int k = 0; k < K; k++) {
        if (numSamples > 1) theta[m][k] *= numSamples - 1; // convert from mean to sum
        theta[m][k] += (nd[m][k] + alpha) / (ndsum[m] + Kalpha);
        if (numSamples > 1) theta[m][k] /= numSamples; // convert from sum to mean
      }
    }
  }

  public void updatePhi() {
    double Vbeta = V * beta;
    for (int k = 0; k < K; k++) {
      for (int w = 0; w < V; w++) {
        if (numSamples > 1) phi[k][w] *= numSamples - 1; // convert from mean to sum
        phi[k][w] += (nw[w][k] + beta) / (nwsum[k] + Vbeta);
        if (numSamples > 1) phi[k][w] /= numSamples; // convert from sum to mean
      }
    }
  }

  // for inference
  public void updatePhi(Model trnModel) {
    double Vbeta = trnModel.V * beta;
    for (int k = 0; k < K; k++) {
      for (int _w = 0; _w < V; _w++) {
        if (data.getLid2gid().containsKey(_w)) {
          int id = data.getLid2gid().get(_w);

          if (numSamples > 1) phi[k][_w] *= numSamples - 1; // convert from mean to sum
          phi[k][_w] += (trnModel.nw[id][k] + nw[_w][k] + beta) / (trnModel.nwsum[k] + nwsum[k] + Vbeta);
          if (numSamples > 1) phi[k][_w] /= numSamples; // convert from sum to mean
        } // else ignore words that don't appear in training
      } //end foreach word
    } // end foreach topic
  }

  //---------------------------------------------------------------
  //	I/O Methods
  //---------------------------------------------------------------

  /**
   * Save model
   */
  public boolean saveModel() {
    return saveModel("");
  }

  public boolean saveModel(String modelPrefix) {
    if (!saveModelTAssign(modelDir + File.separator + modelPrefix + modelName + tassignSuffix)) {
      return false;
    }

    if (!saveModelOthers(modelDir + File.separator + modelPrefix + modelName + othersSuffix)) {
      return false;
    }

    if (!saveModelTheta(modelDir + File.separator + modelPrefix + modelName + thetaSuffix)) {
      return false;
    }

    //if (!saveModelPhi(dir + File.separator + modelPrefix + modelName + phiSuffix)) {
    //    return false;
    //}

    if (twords > 0) {
      if (!saveModelTwords(modelDir + File.separator + modelPrefix + modelName + twordsSuffix)) {
        return false;
      }
    }

    if (!data.getLocalDict().writeWordMap(modelDir + File.separator + modelPrefix + modelName + wordMapSuffix)) {
      return false;
    }

    return true;
  }

  /**
   * Save word-topic assignments for this model
   */
  public boolean saveModelTAssign(String filename) {
    int i, j;

    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(
          new FileOutputStream(filename)), "UTF-8"));

      //write docs with topic assignments for words
      //orig write Document in list and word in doc
      /*
      for (i = 0; i < data.getM(); i++) {
        for (j = 0; j < data.getDocs().get(i).getLength(); ++j) {
          writer.write(data.getDocs().get(i).getWords()[j] + ":" + data.getDocs().get(i).getCfs()[j] + ":" + z[i].get(j) + " ");
        }
        writer.write("\n");
      }
      */
      //every line:uid+"\t"+[word:cf:topic+" "]...+"\n"
      //
      for (i = 0; i < data.getM(); i++) {
        String uid = data.getUid(i);
        writer.write(uid + "\t");
        StringBuilder labelsBuilder=new StringBuilder();
        labelsBuilder.append("[");
        int[] labels = data.getDocs().get(i).getLabels();
        if (labels != null && labels.length!=0) {
          for (int k = 0; k < labels.length - 1; k++)
            labelsBuilder.append(labels[k] + ",");
          labelsBuilder.append(labels[labels.length - 1]);
        }
        labelsBuilder.append("]" + "\t");
        //System.out.println(labelsBuilder.toString());
        writer.write(labelsBuilder.toString());
        for (j = 0; j < data.getDocs().get(i).getLength(); ++j) {
          writer.write(data.getDocs().get(i).getWords()[j] + ":" + data.getDocs().get(i).getCfs()[j] + ":" + z[i].get(j) + " ");
        }
        writer.write("\n");
      }

      writer.close();
    } catch (Exception e) {
      System.out.println("Error while saving model tassign: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Save theta (topic distribution) for this model
   */

  public boolean saveModelTheta(String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(
          new FileOutputStream(filename)), "UTF-8"));

      for (int i = 0; i < M; i++) {
        writer.write(data.getUid(i) + "\t");
        for (int j = 0; j < K; j++) {
          if (theta[i][j] > 0) {
            writer.write(j + ":" + theta[i][j] + " ");
          }
        }
        writer.write("\n");
      }
      writer.close();
    } catch (Exception e) {
      System.out.println("Error while saving topic distribution file for this model: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Save word-topic distribution
   */
  public boolean saveModelPhi(String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(
          new FileOutputStream(filename)), "UTF-8"));

      for (int i = 0; i < K; i++) {
        for (int j = 0; j < V; j++) {
          if (phi[i][j] > 0) {
            writer.write(j + ":" + phi[i][j] + " ");
          }
        }
        writer.write("\n");
      }
      writer.close();
    } catch (Exception e) {
      System.out.println("Error while saving word-topic distribution:" + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Save other information of this model
   */
  public boolean saveModelOthers(String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(
          new FileOutputStream(filename)), "UTF-8"));

      writer.write("alpha=" + alpha + "\n");
      writer.write("beta=" + beta + "\n");
      writer.write("ntopics=" + K + "\n");
      writer.write("ndocs=" + M + "\n");
      writer.write("nwords=" + V + "\n");
      writer.write("liters=" + liter + "\n");

      writer.close();
    } catch (Exception e) {
      System.out.println("Error while saving model others:" + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Save model the most likely words for each topic
   */
  public boolean saveModelTwords(String filename) {
    try {
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
        new GZIPOutputStream(
          new FileOutputStream(filename)), "UTF-8"));

      if (twords > V) {
        twords = V;
      }

      for (int k = 0; k < K; k++) {
        ArrayList<Pair> wordsProbsList = new ArrayList<Pair>();
        for (int w = 0; w < V; w++) {
          Pair p = new Pair(w, phi[k][w], false);

          wordsProbsList.add(p);
        }//end foreach word

        //print topic
        writer.write("Topic " + k + ":\n");
        Collections.sort(wordsProbsList);

        for (int i = 0; i < twords; i++) {
          if (data.getLocalDict().contains((Integer) wordsProbsList.get(i).getFirst())) {
            String word = data.getLocalDict().getWord((Integer) wordsProbsList.get(i).getFirst());

            writer.write("\t" + word + "\t" + wordsProbsList.get(i).getSecond() + "\n");
          }
        }
      } //end foreach topic

      writer.close();
    } catch (Exception e) {
      System.out.println("Error while saving model twords: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Load saved model
   */
  public boolean loadModel() {
    if (!readOthersFile(modelDir + File.separator + modelName + othersSuffix))
      return false;

    if (!readTAssignFile(modelDir + File.separator + modelName + tassignSuffix))
      return false;

    // read dictionary
    com.elex.bigdata.jgibblda.Dictionary dict = new com.elex.bigdata.jgibblda.Dictionary();
    if (!dict.readWordMap(modelDir + File.separator + modelName + wordMapSuffix))
      return false;

    data.setLocalDict(dict);

    return true;
  }

  /**
   * Load "others" file to get parameters
   */
  protected boolean readOthersFile(String otherFile) {
    try {
      BufferedReader reader = new BufferedReader(new InputStreamReader(
        new GZIPInputStream(
          new FileInputStream(otherFile)), "UTF-8"));
      String line;
      while ((line = reader.readLine()) != null) {
        StringTokenizer tknr = new StringTokenizer(line, "= \t\r\n");

        int count = tknr.countTokens();
        if (count != 2)
          continue;

        String optstr = tknr.nextToken();
        String optval = tknr.nextToken();

        if (optstr.equalsIgnoreCase("alpha")) {
          alpha = Double.parseDouble(optval);
        } else if (optstr.equalsIgnoreCase("beta")) {
          beta = Double.parseDouble(optval);
        } else if (optstr.equalsIgnoreCase("ntopics")) {
          K = Integer.parseInt(optval);
        } else if (optstr.equalsIgnoreCase("liter")) {
          liter = Integer.parseInt(optval);
        } else if (optstr.equalsIgnoreCase("nwords")) {
          V = Integer.parseInt(optval);
        } else if (optstr.equalsIgnoreCase("ndocs")) {
          M = Integer.parseInt(optval);
        } else {
          // any more?
        }
      }

      reader.close();
    } catch (Exception e) {
      System.out.println("Error while reading other file:" + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  /**
   * Load word-topic assignments for this model
   */
  protected boolean readTAssignFile(String tassignFile) {
    try {
      long t1=System.currentTimeMillis();
      int i, j;
      BufferedReader reader = new BufferedReader(new InputStreamReader(
        new GZIPInputStream(
          new FileInputStream(tassignFile)), "UTF-8"));

      String line;
      z = new TIntArrayList[M];
      data = new LDADataset();
      data.setM(M);
      data.setV(V);
      for (i = 0; i < M; i++) {
        line = reader.readLine();
        //get uid;the other is word:cf:topic split by " ";
        String uid = line.substring(0, line.indexOf("\t"));
        line = line.substring(line.indexOf("\t") + 1);
        TIntArrayList labels = null;
        if (line.startsWith("[")) {
          String[] labelsBoundary = line.
            substring(1). // remove initial '['
            split("]", 2); // separate labels and str between ']'
          String[] labelStrs = labelsBoundary[0].trim().split(",");
          line = labelsBoundary[1].trim();

          // parse labels (unless we're ignoring the labels)
          if (!unlabeled) {
            // store labels in a HashSet to ensure uniqueness
            TIntHashSet label_set = new TIntHashSet();
            for (String labelStr : labelStrs) {
              try {
                label_set.add(Integer.parseInt(labelStr));
              } catch (NumberFormatException nfe) {
                //System.err.println("Unknown document label ( " + labelStr + " ) for document " + i + ".");
              }
            }
            labels = new TIntArrayList(label_set);
            labels.sort();
          }
        }

        StringTokenizer tknr = new StringTokenizer(line, " ");
        //orig word:cf:topic is split by "\t"
        //StringTokenizer tknr = new StringTokenizer(line, " \t\r\n");

        int length = tknr.countTokens();

        TIntArrayList words = new TIntArrayList();
        TIntArrayList cfs = new TIntArrayList();
        TIntArrayList topics = new TIntArrayList();
        for (j = 0; j < length; j++) {
          String token = tknr.nextToken();

          StringTokenizer tknr2 = new StringTokenizer(token, ":");
          if (tknr2.countTokens() != 3) {
            System.out.println("Invalid word-topic assignment line\n");
            return false;
          }

          words.add(Integer.parseInt(tknr2.nextToken()));
          cfs.add(Integer.parseInt(tknr2.nextToken()));
          topics.add(Integer.parseInt(tknr2.nextToken()));
        }//end for each topic assignment

        //allocate and add new document to the corpus
        Document doc = new Document(words, cfs, line, labels);
        data.setDoc(doc, i);
        //set uid-docNum(Order) map
        data.setUidDocNum(uid, i);
        //assign values for z
        z[i] = new TIntArrayList();
        for (j = 0; j < topics.size(); j++) {
          z[i].add(topics.get(j));
        }

      }//end for each doc

      reader.close();
      long t2=System.currentTimeMillis();
      System.out.println("read tassing file use "+(t2-t1)+" ms");
    } catch (Exception e) {
      System.out.println("Error while loading model: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public static String getTassignSuffix() {
    return tassignSuffix;
  }

  public static void setTassignSuffix(String tassignSuffix) {
    Model.tassignSuffix = tassignSuffix;
  }

  public static String getThetaSuffix() {
    return thetaSuffix;
  }

  public static void setThetaSuffix(String thetaSuffix) {
    Model.thetaSuffix = thetaSuffix;
  }

  public static String getPhiSuffix() {
    return phiSuffix;
  }

  public static void setPhiSuffix(String phiSuffix) {
    Model.phiSuffix = phiSuffix;
  }

  public static String getOthersSuffix() {
    return othersSuffix;
  }

  public static void setOthersSuffix(String othersSuffix) {
    Model.othersSuffix = othersSuffix;
  }

  public static String getTwordsSuffix() {
    return twordsSuffix;
  }

  public static void setTwordsSuffix(String twordsSuffix) {
    Model.twordsSuffix = twordsSuffix;
  }

  public static String getWordMapSuffix() {
    return wordMapSuffix;
  }

  public static void setWordMapSuffix(String wordMapSuffix) {
    Model.wordMapSuffix = wordMapSuffix;
  }

  public String getDir() {
    return dir;
  }

  public void setDir(String dir) {
    this.dir = dir;
  }

  public String getDocDir() {
    return docDir;
  }
  public void setDocDir(String dir){
    this.docDir=dir;
  }

  public String getModelDir(){
    return modelDir;
  }
  public void setModelDir(String dir){
    this.modelDir=dir;
  }

  public String getDfile() {
    return dfile;
  }

  public void setDfile(String dfile) {
    this.dfile = dfile;
  }

  public boolean isUnlabeled() {
    return unlabeled;
  }

  public void setUnlabeled(boolean unlabeled) {
    this.unlabeled = unlabeled;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public LDADataset getData() {
    return data;
  }

  public void setData(LDADataset data) {
    this.data = data;
  }

  public int getM() {
    return M;
  }

  public void setM(int m) {
    M = m;
  }

  public int getV() {
    return V;
  }

  public void setV(int v) {
    V = v;
  }

  public int getK() {
    return K;
  }

  public void setK(int k) {
    K = k;
  }

  public double getAlpha() {
    return alpha;
  }

  public void setAlpha(double alpha) {
    this.alpha = alpha;
  }

  public double getBeta() {
    return beta;
  }

  public void setBeta(double beta) {
    this.beta = beta;
  }

  public int getNiters() {
    return niters;
  }

  public void setNiters(int niters) {
    this.niters = niters;
  }

  public int getNburnin() {
    return nburnin;
  }

  public void setNburnin(int nburnin) {
    this.nburnin = nburnin;
  }

  public int getSamplingLag() {
    return samplingLag;
  }

  public void setSamplingLag(int samplingLag) {
    this.samplingLag = samplingLag;
  }

  public int getNumSamples() {
    return numSamples;
  }

  public void setNumSamples(int numSamples) {
    this.numSamples = numSamples;
  }

  public int getLiter() {
    return liter;
  }

  public void setLiter(int liter) {
    this.liter = liter;
  }

  public int getTwords() {
    return twords;
  }

  public void setTwords(int twords) {
    this.twords = twords;
  }

  public double[][] getTheta() {
    return theta;
  }

  public void setTheta(double[][] theta) {
    this.theta = theta;
  }

  public double[][] getPhi() {
    return phi;
  }

  public void setPhi(double[][] phi) {
    this.phi = phi;
  }

  public TIntArrayList[] getZ() {
    return z;
  }

  public void setZ(TIntArrayList[] z) {
    this.z = z;
  }

  public int[][] getNw() {
    return nw;
  }

  public void setNw(int[][] nw) {
    this.nw = nw;
  }

  public int[][] getNd() {
    return nd;
  }

  public void setNd(int[][] nd) {
    this.nd = nd;
  }

  public int[] getNwsum() {
    return nwsum;
  }

  public void setNwsum(int[] nwsum) {
    this.nwsum = nwsum;
  }

  public int[] getNdsum() {
    return ndsum;
  }

  public void setNdsum(int[] ndsum) {
    this.ndsum = ndsum;
  }

  public ArrayList<TIntObjectHashMap<int[]>> getNw_inf() {
    return nw_inf;
  }

  public void setNw_inf(ArrayList<TIntObjectHashMap<int[]>> nw_inf) {
    this.nw_inf = nw_inf;
  }

  public int[][] getNwsum_inf() {
    return nwsum_inf;
  }

  public void setNwsum_inf(int[][] nwsum_inf) {
    this.nwsum_inf = nwsum_inf;
  }

  public double[] getP() {
    return p;
  }

  public void setP(double[] p) {
    this.p = p;
  }
}
