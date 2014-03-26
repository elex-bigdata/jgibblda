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

import java.io.FileNotFoundException;
import java.io.IOException;

public class Estimator
{
  // output model
  private Model trnModel;
  private LDACmdOption option;

  public Estimator(LDACmdOption option) throws FileNotFoundException, IOException
  {
    this.option = option;

    trnModel = new Model(option);

    if (option.est){
      trnModel.init(true,true);
    }
    else if (option.estc){
      trnModel.init(false,true);
    }
  }

  public void estimate()
  {
    System.out.println("Sampling " + trnModel.getNiters() + " iterations!");
    System.out.print("Iteration");
    int liter =trnModel.getLiter();
    for (int startIter = ++liter; liter <= startIter - 1 + trnModel.getNiters(); liter++){
      System.out.format("%6d", liter);

      // for all z_i
      for (int m = 0; m < trnModel.getM(); m++){
        for (int n = 0; n < trnModel.getData().getDocs().get(m).getLength(); n++){
          // z_i = z[m][n]
          // sample from p(z_i|z_-i, w)
          int topic = sampling(m, n);
          trnModel.getZ()[m].set(n, topic);
        }// end for each word
      }// end for each document

      if ((liter == startIter - 1 + trnModel.getNiters()) ||
        (liter > trnModel.getNburnin() && liter % trnModel.getSamplingLag() == 0)) {
        trnModel.updateParams();
      }

      System.out.print("\b\b\b\b\b\b");
    }// end iterations
    trnModel.setLiter(liter-1);

    System.out.println("\nSaving the final model!");
    trnModel.saveModel();
  }

  /**
   * Do sampling
   * @param m document number
   * @param n word number
   * @return topic id
   */
  public int sampling(int m, int n)
  {
    // remove z_i from the count variable
    int topic = trnModel.getZ()[m].get(n);
    int w = trnModel.getData().getDocs().get(m).getWords()[n];
    int cf=trnModel.getData().getDocs().get(m).getCfs()[n];

    trnModel.getNw()[w][topic] -= cf;
    trnModel.getNd()[m][topic] -= cf;
    trnModel.getNwsum()[topic] -= cf;
    trnModel.getNdsum()[m] -= cf;

    double Vbeta = trnModel.getV() * trnModel.getBeta();

    // get labels for this document
    int[] labels = trnModel.getData().getDocs().get(m).getLabels();

    // determine number of possible topics for this document
    int K_m = (labels == null || labels.length==0) ? trnModel.getK() : labels.length;

    // do multinominal sampling via cumulative method
    double[] p = trnModel.getP();
    for (int k = 0; k < K_m; k++) {
      topic = (labels == null || labels.length==0) ? k : labels[k];

      p[k] = (trnModel.getNd()[m][topic] + trnModel.getAlpha()) *
        (trnModel.getNw()[w][topic] + trnModel.getBeta()) /
        (trnModel.getNwsum()[topic] + Vbeta);
    }

    // cumulate multinomial parameters
    for (int k = 1; k < K_m; k++) {
      p[k] += p[k - 1];
    }

    // scaled sample because of unnormalized p[]
    double u = Math.random() * p[K_m - 1];

    for (topic = 0; topic < K_m; topic++){
      if (p[topic] > u) //sample topic w.r.t distribution p
        break;
    }

    // map [0, K_m - 1] topic to [0, K - 1] topic according to labels
    if (labels != null && labels.length!=0) {
      topic = labels[topic];
    }

    // add newly estimated z_i to count variables
    trnModel.getNw()[w][topic] += cf;
    trnModel.getNd()[m][topic] += cf;
    trnModel.getNwsum()[topic] += cf;
    trnModel.getNdsum()[m] += cf;

    return topic;
  }
}
