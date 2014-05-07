package com.elex.bigdata.jgibblda;

import org.junit.Test;

import java.io.File;
import java.util.Date;

/**
 * Created with IntelliJ IDEA.
 * User: yb
 * Date: 5/7/14
 * Time: 3:04 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestOthers {
  @Test
  public void testDate(){
    Date date=new Date();
    int minutes=date.getMinutes()+date.getHours()*60;
    int index=minutes/5;
    int day=date.getDay();
    System.out.println(index+" "+day);
  }
  @Test
  public void testPath(){
     String origPath="/data/log/user_category";
     String parentPath=new File(origPath).getParent();
     String name=new File(origPath).getName();
     System.out.println("parent: "+parentPath+" name: "+name);
  }
}
