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

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.elex.bigdata.util.MetricMapping;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.*;

public class LDA
{
  private static Logger logger=Logger.getLogger(LDA.class);
  public static void main(String args[])
  {
    LDACmdOption option = new LDACmdOption();
    CmdLineParser parser = new CmdLineParser(option);
    ExecutorService service=new ThreadPoolExecutor(3,20,3600, TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(30));
    try {
      if (args.length == 0){
        showHelp(parser);
        return;
      }

      parser.parseArgument(args);

      String modelDir,docDir;
      List<String> projects=new ArrayList<String>();
      if(option.project.equals("")){
        //todo
        //get all projects to add to projects list
        for(String project : MetricMapping.getInstance().getAllProjectShortNameMapping().keySet())
          projects.add(project);
      }else{
        projects.add(option.project);
      }
      for(String project:projects){
        Byte projectId= MetricMapping.getInstance().getProjectURLByte(project);
        if(option.nation.equals("")){
          Set<String> nations=MetricMapping.getNationsByProjectID(projectId);
          for(String nation :nations){
            modelDir=option.modelDir+(option.modelDir.endsWith(File.separator)?"":File.separator)+project+File.separator+nation;
            docDir=option.docDir+(option.docDir.endsWith(File.separator)?"":File.separator)+project+File.separator+nation;
            if (option.est || option.estc){
              Estimator estimator = new Estimator(option,modelDir,docDir);
              service.execute(estimator);
            }
            else if (option.inf){
              Inferencer inferencer = new Inferencer(option,modelDir,docDir);
              service.execute(inferencer);
            }
          }
        }else{
          modelDir=option.modelDir+(option.modelDir.endsWith(File.separator)?"":File.separator)+project+File.separator+option.nation;
          docDir=option.docDir+(option.docDir.endsWith(File.separator)?"":File.separator)+project+File.separator+option.nation;
          if (option.est || option.estc){
            Estimator estimator = new Estimator(option,modelDir,docDir);
            service.execute(estimator);
          }
          else if (option.inf){
            Inferencer inferencer = new Inferencer(option,modelDir,docDir);
            service.execute(inferencer);
          }
        }
      }
      service.shutdown();
      service.awaitTermination(3,TimeUnit.HOURS);

    } catch (CmdLineException cle){
      System.out.println("Command line error: " + cle.getMessage());
      showHelp(parser);
      return;
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return;
    } catch (Exception e){
      System.out.println("Error in main: " + e.getMessage());
      e.printStackTrace();
      return;
    }
  }

  public static void showHelp(CmdLineParser parser){
    System.out.println("LDA [options ...] [arguments...]");
    parser.printUsage(System.out);
  }

}
