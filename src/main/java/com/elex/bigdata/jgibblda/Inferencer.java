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

import com.elex.bigdata.hashing.HashingException;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class Inferencer implements Runnable
{
  private static Logger logger=Logger.getLogger(Inferencer.class);
  // Train model
  private Model trnModel;
  private Dictionary globalDict;
  private LDACmdOption option;

  private Model newModel;

  //-----------------------------------------------------
  // Init method
  //-----------------------------------------------------
  public Inferencer(LDACmdOption option) throws FileNotFoundException, IOException
  {
    this.option = option;

    trnModel = new Model(option);
    trnModel.init(false,false);

    globalDict = trnModel.getData().getLocalDict();
  }

  public Inferencer(LDACmdOption option,String docDir,String modelDir) throws IOException {
    this.option = option;

    trnModel = new Model(option,docDir,modelDir);
    trnModel.init(false,false);

    globalDict = trnModel.getData().getLocalDict();
  }

  //inference new model ~ getting data from a specified dataset
  public Model inference() throws FileNotFoundException, IOException, HashingException {
    newModel = new Model(option, trnModel.getDocDir(),trnModel.getModelDir(),trnModel);
    //merge documents in trainedModel which has same uid with newModel
    newModel.getData().mergeTrainedDocuments(trnModel.getData(), false);
    newModel.init(true,false);
    newModel.initInf();
    logger.info("inference "+newModel.getModelDir()+newModel.getDfile()+" start");
    logger.info("Sampling " + newModel.getNiters() + " iterations for inference!");
    logger.info("Iteration");
    int liter=1;
    for (liter = 1; liter <= newModel.getNiters(); liter++){


      // for all newz_i
      for (int m = 0; m < newModel.getM(); ++m){
        for (int n = 0; n < newModel.getData().getDocs().get(m).getLength(); n++){
          // sample from p(z_i|z_-1,w)
          int topic = infSampling(m, n);
          newModel.getZ()[m].set(n, topic);
        }
      }//end foreach new doc

      if ((liter == newModel.getNiters()) ||
        (liter > newModel.getNburnin() && liter % newModel.getSamplingLag() == 0)) {
        if(liter%50==0)
          logger.info(liter);
        newModel.updateParams(trnModel);
      }

      //System.out.print("\b\b\b\b\b\b");
    }// end iterations
    newModel.setLiter(liter-1);

//        System.out.println("\nSaving the inference outputs!");
//        String outputPrefix = newModel.getDfile();
//        if (outputPrefix.endsWith(".gz")) {
//            outputPrefix = outputPrefix.substring(0, outputPrefix.length() - 3);
//        }
//        newModel.saveModel(outputPrefix + ".");
    logger.info("\nSaving the inference outputs!");
    String modelPrefix=newModel.getDfile().replace('/','.')+"_inf_";
    String tassignSuffix=".tassign.gz";
    newModel.saveModel(modelPrefix);
    String resultFile=newModel.getModelDir()+ File.separator + modelPrefix + newModel.getModelName()+tassignSuffix;
    logger.info("result Etl start");
    ResultEtl resultEtl=new ResultEtl();
    resultEtl.loadResult(resultFile);
    resultEtl.putToRedis();
    logger.info("result Etl completed");
    logger.info("inference "+newModel.getModelDir()+ "completely");
      /*
    for(int i=0;i<newModel.getM();i++){
      System.out.print(newModel.getData().getUid(i)+"\t");
      for(int j=0;j<newModel.getK();j++){
        System.out.print(result[i][j]+" ");
      }
      System.out.print("\n\r");
    }
    */
    return newModel;

  }

  /**
   * do sampling for inference
   * m: document number
   * n: word number?
   */
  protected int infSampling(int m, int n)
  {
    // remove z_i from the count variables
    int topic = newModel.getZ()[m].get(n);
    int _w = newModel.getData().getDocs().get(m).getWords()[n];
    int cf = newModel.getData().getDocs().get(m).getCfs()[n];
    int w = newModel.getData().getLid2gid().get(_w);

    newModel.getNw()[_w][topic] -= cf;
    newModel.getNd()[m][topic] -= cf;
    newModel.getNwsum()[topic] -= cf;
    newModel.getNdsum()[m] -= cf;

    int[] nw_inf_m__w = null;
    if (option.infSeparately) {
      nw_inf_m__w = newModel.getNw_inf().get(m).get(_w);
      nw_inf_m__w[topic] -= cf;
      newModel.getNwsum_inf()[m][topic] -= cf;
    }

    double Vbeta = trnModel.getV() * newModel.getBeta();

    // get labels for this document
    int[] labels = newModel.getData().getDocs().get(m).getLabels();

    // determine number of possible topics for this document
    int K_m = (labels == null || labels.length==0) ? newModel.getK() : labels.length;

    // do multinomial sampling via cumulative method
    double[] p = newModel.getP();
    for (int k = 0; k < K_m; k++) {
      topic = (labels == null || labels.length==0) ? k : labels[k];

      int nw_k, nwsum_k;
      if (option.infSeparately) {
        nw_k = nw_inf_m__w[topic];
        nwsum_k = newModel.getNwsum_inf()[m][topic];
      } else {
        nw_k = newModel.getNw()[_w][topic];
        nwsum_k = newModel.getNwsum()[topic];
      }

      p[k] = (newModel.getNd()[m][topic] + newModel.getAlpha()) *
        (trnModel.getNw()[w][topic] + nw_k + newModel.getBeta()) /
        (trnModel.getNwsum()[topic] + nwsum_k + Vbeta);
      /*
      if(p[k]<0)
        System.err.print("error");
        */
    }

    // cumulate multinomial parameters
    for (int k = 1; k < K_m; k++){
      p[k] += p[k - 1];
    }

    // scaled sample because of unnormalized p[]
    double u = Math.random() * p[K_m - 1];

    for (topic = 0; topic < K_m; topic++){
      if (p[topic] > u)
        break;
    }

    // map [0, K_m - 1] topic to [0, K - 1] topic according to labels
    if (labels != null && labels.length!=0) {
      /*
      if(topic>=labels.length)
      {
        System.err.print("error");
      }
      */
      topic = labels[topic];
    }

    // add newly estimated z_i to count variables
    newModel.getNw()[_w][topic] += cf;
    newModel.getNd()[m][topic] += cf;
    newModel.getNwsum()[topic] += cf;
    newModel.getNdsum()[m] +=cf;

    if (option.infSeparately) {
      nw_inf_m__w[topic] += cf;
      newModel.getNwsum_inf()[m][topic] += cf;
    }

    return topic;
  }

  @Override
  public void run() {
    try {
      inference();
    } catch (IOException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (HashingException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
  }
}
