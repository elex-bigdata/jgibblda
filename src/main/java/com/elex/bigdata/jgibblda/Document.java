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

import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.hash.TIntHashSet;

public class Document {

  //----------------------------------------------------
  //Instance Variables
  //----------------------------------------------------
  private int[] words;
  private int[] cfs;
  private String rawStr = "";
  private int length;
  private int[] labels = null;

  public Document(TIntArrayList doc,TIntArrayList cfs){
    this.length = doc.size();
    this.words = new int[length];
    this.cfs= new int[length];
    for (int i = 0; i < length; i++){
      this.words[i] = doc.get(i);
      this.cfs[i] = cfs.get(i);
    }
  }

  public Document(TIntArrayList doc,TIntArrayList cfs, String rawStr)
  {
    this(doc,cfs);
    this.rawStr = rawStr;
  }

  public Document(TIntArrayList doc,TIntArrayList cfs, String rawStr, TIntArrayList tlabels)
  {
    this(doc,cfs, rawStr);
    this.labels = tlabels != null ? tlabels.toArray() : null;
  }

  public int[] getWords() {
    return words;
  }

  public void setWords(int[] words) {
    this.words = words;
  }

  public int[] getCfs() {
    return cfs;
  }

  public void setCfs(int[] cfs) {
    this.cfs = cfs;
  }

  public String getRawStr() {
    return rawStr;
  }

  public void setRawStr(String rawStr) {
    this.rawStr = rawStr;
  }

  public int getLength() {
    return length;
  }

  public void setLength(int length) {
    this.length = length;
  }

  public int[] getLabels() {
    return labels;
  }

  public void setLabels(int[] labels) {
    this.labels = labels;
  }

  public void mergeDoc(Document document){
    //merge labels
    TIntHashSet labelSet=new TIntHashSet(labels);
    for(int label: document.getLabels()){
      labelSet.add(label);
    }
    TIntArrayList labelList=new TIntArrayList(labelSet);
    //sort labels
    labelList.sort();
    labels=labelList.toArray();

    //merge word:cf
    //put the inferenced word-cf to map
    TIntIntHashMap word_cf_map=new TIntIntHashMap();
    for(int i=0;i<words.length;i++){
      word_cf_map.put(words[i],cfs[i]);
    }
    //loop for trained words to see merge word-cf
    TIntArrayList mergedWords=new TIntArrayList();
    TIntArrayList mergedCfs=new TIntArrayList();
    int[] trainedWords=document.getWords();
    int[] trainedCfs=document.getCfs();
    for(int i=0;i<trainedWords.length;i++){
      mergedWords.add(trainedWords[i]);
      mergedCfs.add(trainedCfs[i]);
      if(word_cf_map.containsKey(trainedWords[i]))
      {
         mergedCfs.set(i,trainedWords[i]+word_cf_map.get(trainedWords[i]));
         word_cf_map.remove(trainedWords[i]);
      }
    }
    TIntIntIterator iterator= word_cf_map.iterator();
    while(iterator.hasNext()){
      mergedWords.add(iterator.key());
      mergedCfs.add(iterator.value());
      iterator.advance();
    }
    words=mergedWords.toArray();
    cfs=mergedCfs.toArray();
  }
}
