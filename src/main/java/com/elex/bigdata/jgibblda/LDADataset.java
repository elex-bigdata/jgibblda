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

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

public class LDADataset {
  //---------------------------------------------------------------
  // Instance Variables
  //---------------------------------------------------------------

  private Dictionary localDict = new Dictionary();      // local dictionary
  private ArrayList<Document> docs = new ArrayList<Document>();    // a list of documents
  private Map<String, Integer> uidDocNums = new HashMap<String, Integer>();
  private Map<Integer, String> docNumUids = new HashMap<Integer, String>();
  private int M = 0;          // number of documents
  private int V = 0;          // number of words

  // map from local coordinates (id) to global ones
  // null if the global dictionary is not set
  private TIntIntHashMap lid2gid = null;
  private TIntIntHashMap gid2lid = null;

  //link to a global dictionary (optional), null for train data, not null for test data
  private Dictionary globalDict = null;

  private Set<String> eliminatedUrls = new HashSet<String>();

  public LDADataset() {
    InputStream inputStream = this.getClass().getResourceAsStream("/eliminated_urls");
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
    String line = null;
    try {
      while ((line = reader.readLine()) != null) {
        String[] urls = line.split(" ");
        for (String url : urls) {
          eliminatedUrls.add(url);
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  //-------------------------------------------------------------
  //Public Instance Methods
  //-------------------------------------------------------------
  public void setM(int M) {
    this.M = M;
  }

  public void setDictionary(Dictionary globalDict) {
    lid2gid = new TIntIntHashMap();
    gid2lid = new TIntIntHashMap();
    this.globalDict = globalDict;
  }

  /**
   * set the document at the index idx if idx is greater than 0 and less than M
   *
   * @param doc document to be set
   * @param idx index in the document array
   */
  public void setDoc(Document doc, int idx) {
    if (idx < docs.size()) {
      docs.set(idx, doc);
    } else {
      docs.add(idx, doc);
    }
  }

  /**
   * add a new document
   *
   * @param str string contains doc
   */
  public void addDoc(String str, boolean unlabeled) {

    //read uid
    if (!str.contains("\t"))
      return;
    String uid = str.substring(0, str.indexOf('\t'));
    str = str.substring(str.indexOf('\t') + 1).trim();

    // read document labels (if provided)
    TIntArrayList labels = null;
    if (str.startsWith("[")) {
      String[] labelsBoundary = str.
        substring(1). // remove initial '['
        split("]", 2); // separate labels and str between ']'
      String[] labelStrs = labelsBoundary[0].trim().split(",");
      str = labelsBoundary[1].trim();

      // parse labels (unless we're ignoring the labels)
      if (!unlabeled) {
        // store labels in a HashSet to ensure uniqueness
        TIntHashSet label_set = new TIntHashSet();
        for (String labelStr : labelStrs) {
          try {
            label_set.add(Integer.parseInt(labelStr));
          } catch (NumberFormatException nfe) {
            //System.err.println("Unknown document label ( " + labelStr + " ) for document " + docs.size() + ".");
          }
        }
        labels = new TIntArrayList(label_set);
        labels.sort();
      }
    }

    String[] items = str.split(" ");
    TIntArrayList ids = new TIntArrayList();
    TIntArrayList cfs = new TIntArrayList();

    for (String item : items) {
      if (item.trim().equals("")) {
        continue;
      }
      String[] wordCf = item.split(",");
      if (wordCf.length != 2) {
        continue;
      }
      try {
        String word = wordCf[0];
        if(eliminatedUrls.contains(word))
          continue;
        int cf = Integer.parseInt(wordCf[1]);
        int _id = localDict.getWord2id().size();

        if (localDict.contains(word))
          _id = localDict.getID(word);

        if (globalDict != null) {
          //get the global id
          if (globalDict.contains(word)) {
            localDict.addWord(word);

            lid2gid.put(_id, globalDict.getID(word));
            gid2lid.put(globalDict.getID(word), _id);
            ids.add(_id);
            cfs.add(cf);
          }
        } else {
          localDict.addWord(word);
          ids.add(_id);
          cfs.add(cf);
        }
      } catch (NumberFormatException e) {
        e.printStackTrace();
        continue;
      }
    }
    if(ids.size()==0)
      return;
    Document doc = new Document(ids, cfs, str, labels);
    setDoc(doc, docs.size());
    //put uid and Doc into map
    uidDocNums.put(uid, docs.size());
    docNumUids.put(docs.size(), uid);
    V = localDict.getWord2id().size();
  }

  //---------------------------------------------------------------
  // I/O methods
  //---------------------------------------------------------------

  /**
   * read a dataset from a file
   *
   * @return true if success and false otherwise
   */
  public boolean readDataSet(String filename, boolean unlabeled) throws FileNotFoundException, IOException {
    BufferedReader reader = null;
    if (filename.endsWith(".gz"))
      reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(filename)), "UTF-8"));
    else
      reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        addDoc(line, unlabeled);
      }
      setM(docs.size());

      // debug output
      System.out.println("Dataset loaded:");
      System.out.println("\tM:" + M);
      System.out.println("\tV:" + V);

      return true;
    } finally {
      reader.close();
    }
  }

  public Dictionary getLocalDict() {
    return localDict;
  }

  public void setLocalDict(Dictionary localDict) {
    this.localDict = localDict;
  }

  public ArrayList<Document> getDocs() {
    return docs;
  }

  public String getUid(Integer docNum) {
    return docNumUids.get(docNum);
  }

  public Integer getDocNum(String uid) {
    return uidDocNums.get(uid);
  }

  public void setUidDocNum(String uid, Integer docNum) {
    uidDocNums.put(uid, docNum);
    docNumUids.put(docNum, uid);
  }


  public void setDocs(ArrayList<Document> docs) {
    this.docs = docs;
  }

  public void removeDoc(Integer docNum) {
    docs.remove(docNum);
    String uid = getUid(docNum);
    uidDocNums.remove(uid);
    docNumUids.remove(docNum);
    for (int i = docNum + 1; i < docs.size() + 1; i++) {
      uidDocNums.put(docNumUids.get(i), i - 1);
      docNumUids.put(i - 1, docNumUids.get(i));
    }
  }

  public int getM() {
    return M;
  }

  public int getV() {
    return V;
  }

  public void setV(int v) {
    V = v;
  }

  public TIntIntHashMap getLid2gid() {
    return lid2gid;
  }

  public void setLid2gid(TIntIntHashMap lid2gid) {
    this.lid2gid = lid2gid;
  }

  public Dictionary getGlobalDict() {
    return globalDict;
  }

  public void setGlobalDict(Dictionary globalDict) {
    this.globalDict = globalDict;
  }

  public void mergeTrainedDocuments(LDADataset trainedData, boolean est) {
    for (int i = 0; i < docs.size(); i++) {
      String uid = getUid(i);
      Integer trainedDocNum = trainedData.getDocNum(uid);
      if (trainedDocNum == null)
        continue;
      Document trainedDoc = trainedData.getDocs().get(trainedDocNum);
      //docs.get(i).mergeDoc(trainedDoc);
      mergeDocument(docs.get(i), trainedDoc, est);
    }
    setV(localDict.getWord2id().size());
    System.out.println("localDict size is " + V);
  }

  //used by train -estc or inference

  public void mergeDocument(Document currentDoc, Document trainedDoc, boolean est) {

    //merge labels
    TIntHashSet labelSet = new TIntHashSet(currentDoc.getLabels());
    if (trainedDoc.getLabels() != null)
      for (int label : trainedDoc.getLabels()) {
        labelSet.add(label);
      }
    TIntArrayList labelList = new TIntArrayList(labelSet);
    //sort labels
    labelList.sort();
    currentDoc.setLabels(labelList.toArray());


    //merge word:cf
    //put the inferenced word-cf to map
    Map<Integer, Integer> word_cf_map = new HashMap<Integer, Integer>();
    for (int i = 0; i < currentDoc.getWords().length; i++) {
      word_cf_map.put(currentDoc.getWords()[i], currentDoc.getCfs()[i]);
    }
    //loop for trained words to see merge word-cf
    TIntArrayList mergedWords = new TIntArrayList();
    TIntArrayList mergedCfs = new TIntArrayList();
    int[] trainedWords = trainedDoc.getWords();
    int[] trainedCfs = trainedDoc.getCfs();
    //current doc and trainedDoc have same dictionary
    if (est) {
      for (int i = 0; i < trainedWords.length; i++) {
        mergedWords.add(trainedWords[i]);
        mergedCfs.add(trainedCfs[i]);
        if (word_cf_map.containsKey(trainedWords[i])) {
          mergedCfs.set(i, trainedCfs[i] + word_cf_map.get(trainedWords[i]));
          word_cf_map.remove(trainedWords[i]);
        }
      }
    } else if (globalDict != null) {
      //current doc has local dict and trainedDoc has globalDict
      int localId = V;
      for (int i = 0; i < trainedWords.length; i++) {
        //if word not in local dict then put it into local dict and map to global dict
        if (!gid2lid.containsKey(trainedWords[i])) {
          gid2lid.put(trainedWords[i], localId);
          lid2gid.put(localId, trainedWords[i]);
          localDict.addWord(globalDict.getWord(trainedWords[i]));
          //dict word order starts with 0 so V should be greater than last word id by one
          localId++;
        }
        int localWord = gid2lid.get(trainedWords[i]);
        mergedWords.add(localWord);
        mergedCfs.add(trainedCfs[i]);
        if (word_cf_map.containsKey(localWord)) {
          mergedCfs.set(i, trainedCfs[i] + word_cf_map.get(localWord));
          word_cf_map.remove(localWord);
        }
      }
      V = localId;
    }
    for (Map.Entry<Integer, Integer> entry : word_cf_map.entrySet()) {
      mergedWords.add(entry.getKey());
      mergedCfs.add(entry.getValue());
    }
    currentDoc.setWords(mergedWords.toArray());
    currentDoc.setCfs(mergedCfs.toArray());
    currentDoc.setLength(mergedWords.size());

  }

}
