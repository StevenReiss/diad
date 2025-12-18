/********************************************************************************/
/*                                                                              */
/*              DitestFactory.java                                              */
/*                                                                              */
/*      Primary entry points for testing                                        */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 * This program and the accompanying materials are made available under the      *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at                                                           *
 *      http://www.eclipse.org/legal/epl-v10.html                                *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.ditest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.junit.Assert;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.diruntime.DiruntimeConstants.RunEventType;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.exec.IvyExecQuery;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

public class DitestFactory implements DitestConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private String          stopped_thread;

private boolean         debug_fait;
private boolean         trace_fait;
private boolean         debug_seede;
private boolean         trace_seede;
private int             seede_timeout;
private boolean         fait_starting;
private boolean         seede_starting;

public static final String [] OPENS;

static {
   OPENS = new String [] { "java.desktop/sun.font",
         "java.base/jdk.internal.icu.impl",
         "java.desktop/sun.awt",
         "java.desktop/sun.swing",
         "java.desktop/javax.swing", 
         "java.base/jdk.internal.math", 
         "java.base/sun.nio.cs", 
         "java.base/java.nio",
         "java.base/sun.util.locale.provider",
         "java.base/jdk.internal.math",
         "java.base/jdk.internal.misc",
         "java.base/sun.util.calendar",
    };
}



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DitestFactory(DicontrolMain ctrl)
{
   diad_control = ctrl;
   stopped_thread = null;
   debug_fait = true;
   trace_fait = false;
   debug_seede = true;
   trace_seede = true;
   seede_timeout = 0;
   fait_starting = false;
   seede_starting = false;
}


/********************************************************************************/
/*                                                                              */
/*      Start eclipse on a project                                              */
/*                                                                              */
/********************************************************************************/

public void setupBedrock(String workspace,String mint)
{
   IvyLog.logI("LIMBA","Starting bedrock/eclipse for debugging");
   
   File ec1 = new File(BROWN_ECLIPSE);
   File ec2 = new File(BROWN_WS);
   if (!ec1.exists()) {
      ec1 = new File(HOME_MAC_ECLIPSE);
      ec2 = new File(HOME_MAC_WS);
    }
   if (!ec1.exists()) {
      ec1 = new File(HOME_LINUX_ECLIPSE);
      ec2 = new File(HOME_LINUX_WS);
    }
   if (!ec1.exists()) {
      System.err.println("Can't find bubbles version of eclipse to run");
      throw new Error("No eclipse");
    }
   ec2 = new File(ec2,workspace);
   
   diad_control.setupMessageServer(mint); 
   
   String cmd = ec1.getAbsolutePath();
   cmd += " -application edu.brown.cs.bubbles.bedrock.application";
   cmd += " -data " + ec2.getAbsolutePath();
   cmd += " -nosplash";
   cmd += " -vmargs -Dedu.brown.cs.bubbles.MINT=" + mint;
   cmd += " -Xmx16000m";
   
   IvyLog.logI("LIMBA","RUN: " + cmd);
   
   try {
      for (int i = 0; i < 250; ++i) {
	 try {
	    Thread.sleep(1000);
	  }
	 catch (InterruptedException e) { }
	 if (diad_control.pingEclipse()) { 
            CommandArgs a1 = new CommandArgs("LEVEL","DEBUG");
	    diad_control.sendBubblesMessage("LOGLEVEL",a1,null);
	    diad_control.sendBubblesMessage("ENTER",null,null);
            startFait();
            startSeede();
	    return;
	  }
	 if (i == 0) {
	    new IvyExec(cmd);
	    diad_control.pongEclipse();  
	  }
       }
    }
   catch (IOException e) {
      IvyLog.logE("LIMBA","Problem with eclipse",e);
    }
   
   throw new Error("Problem running Eclipse: " + cmd);
}



/********************************************************************************/
/*                                                                              */
/*      Start fait for workspace                                                */
/*                                                                              */
/********************************************************************************/

public boolean startFait()
{
   if (fait_starting) return false;
   fait_starting = true;
   
   IvyLog.logD("DITEST","START FAIT");
   
   IvyExec exec = null;
   File wd = new File(System.getProperty("user.home"));
   File logf = new File(wd,"fait.log");
   
   File f3 = new File(wd,".bubbles");
   File f4 = new File(f3,"Diad.props");
   Properties bp = new Properties();
   try (InputStream in = new FileInputStream(f4)) {
      bp.loadFromXML(in);
    }
   catch (IOException e) {
      IvyLog.logE("DITEST","Problem reading properties",e);
    }
   
   List<String> args = new ArrayList<>();
   args.add(IvyExecQuery.getJavaPath());
   
   File f2 = getDropinDirectory();
   File faitjar = new File(f2,"fait.jar");
   
   args.add("-cp");
   String xcp = bp.getProperty("Rose.fait.class.path");
   if (xcp == null) {
      xcp = System.getProperty("java.class.path");
      String ycp = bp.getProperty("Rose.fait.add.path");
      if (ycp != null) xcp = ycp + File.pathSeparator + xcp;
    }
   else {
      StringBuffer buf = new StringBuffer();
      StringTokenizer tok = new StringTokenizer(xcp,":;");
      while (tok.hasMoreTokens()) {
         String elt = tok.nextToken();
         if (!elt.startsWith("/") &&  !elt.startsWith("\\")) {
            if (elt.equals("eclipsejar")) {
               elt = getEclipsePath();
             }
            else if (elt.equals("fait.jar") && faitjar != null) {
               elt = faitjar.getPath();
             }
            else {
               elt = getLibraryPath(elt);
             }
          }
         if (buf.length() > 0) buf.append(File.pathSeparator);
         buf.append(elt);
       }
      xcp = buf.toString();
    }
   
   args.add(xcp);
   args.add("edu.brown.cs.fait.iface.FaitMain");
   args.add("-m");
   args.add(diad_control.getMintId()); 
   args.add("-L");
   args.add(logf.getPath());
   if (debug_fait) {
      args.add("-D");
    }
   if (trace_fait) {
      args.add("-T");
    }
   
   boolean isnew = false;
   for (int i = 0; i < 100; ++i) {
      Element rslt = diad_control.sendFaitMessage("PING",
            new CommandArgs("SID","*"),null);
      if (rslt != null) {
         break;
       }
      if (i == 0) {
         try {
               exec = new IvyExec(args,null,IvyExec.ERROR_OUTPUT);    
               // make IGNORE_OUTPUT to clean up output
            isnew = true;
            IvyLog.logD("DITEST","Run " + exec.getCommand());
          }
         catch (IOException e) {
            break;
          }
       }
      else {
         try {
            if (exec != null) {
               int sts = exec.exitValue();
               IvyLog.logD("DITEST","Fait server disappeared with status " + sts);
               break;
             }
          }
         catch (IllegalThreadStateException e) { }
       }
      
      try {
         Thread.sleep(2000);
       }
      catch (InterruptedException e) { }
    }
   
   fait_starting = false;
   
   return isnew;
}


private String getEclipsePath()
{
   String ourcp = System.getProperty("java.class.path");
   StringTokenizer tok = new StringTokenizer(ourcp,File.pathSeparator);
   StringBuffer rslt = new StringBuffer();
   while (tok.hasMoreTokens()) {
      String pe = tok.nextToken();
      if (pe.contains("org.eclipse.") || pe.contains("com.google.")) {
         if (!rslt.isEmpty()) rslt.append(File.pathSeparator);
         rslt.append(pe);
       }
    }
   
   return rslt.toString();
}


private String getLibraryPath(String elt)
{
   String ourcp = System.getProperty("java.class.path");
   StringTokenizer tok = new StringTokenizer(ourcp,File.pathSeparator);
   while (tok.hasMoreTokens()) {
      String pe = tok.nextToken();
      if (pe.contains(elt)) {
         return pe;
       }
      else if (elt.equals("ivy.jar") && pe.contains("ivy/java")) {
         return pe;
       }
    }
   
   return elt;
}


private File getDropinDirectory()
{
   String pro = System.getenv("PRO");
   if (pro == null) pro = System.getenv("BROWN_IVY_PRO");
   if (pro == null) pro = "/pro";
   File f0 = new File(pro);
   File f1 = new File(f0,"bubbles");
   File f2 = new File(f1,"dropins");
   return f2;
}

/********************************************************************************/
/*                                                                              */
/*      Start SEEDE                                                             */
/*                                                                              */
/********************************************************************************/

public boolean startSeede()
{
   if (seede_starting) return false;
   seede_starting = true;
   
   IvyLog.logD("DITEST","START SEEDE");
   
   IvyExec exec = null;
   File wd = new File(System.getProperty("user.home"));
   File logf = new File(wd,"seede.log");
   boolean isnew = false;
   
   File f3 = new File(wd,".bubbles");
   File f4 = new File(f3,"Diad.props");
   Properties bp = new Properties();
   try (InputStream in = new FileInputStream(f4)) {
      bp.loadFromXML(in);
    }
   catch (IOException e) {
      IvyLog.logE("DITEST","Problem reading properties",e);
    }
   
   List<String> args = new ArrayList<String>();
   args.add(IvyExecQuery.getJavaPath());
   
   for (String s : OPENS) {
      String arg = "--add-opens=" + s + "=ALL-UNNAMED";
      args.add(arg);
    }
   
   File f2 = getDropinDirectory();
   File seedejar = new File(f2,"seede.jar");
   
   args.add("-cp");
   String xcp = bp.getProperty("Rose.seede.class.path");
   if (xcp == null) {
      xcp = System.getProperty("java.class.path");
      String ycp = bp.getProperty("Rose.seede.add.path");
      if (ycp != null) xcp = ycp + File.pathSeparator + xcp;
    }
   else {
      StringBuffer buf = new StringBuffer();
      StringTokenizer tok = new StringTokenizer(xcp,":;");
      while (tok.hasMoreTokens()) {
         String elt = tok.nextToken();
         if (!elt.startsWith("/") &&  !elt.startsWith("\\")) {
            if (elt.equals("eclipsejar")) {
               elt = getEclipsePath();
             }
            else if (elt.equals("seede.jar") && seedejar != null) {
               elt = seedejar.getPath();
             }
            else {
               elt = getLibraryPath(elt);
             }
          }
         if (buf.length() > 0) buf.append(File.pathSeparator);
         buf.append(elt);
       }
      xcp = buf.toString();
    }
   
   args.add(xcp);
   args.add("edu.brown.cs.seede.sesame.SeedeMain");
   args.add("-m");
   args.add(diad_control.getMintId());
   args.add("-L");
   args.add(logf.getPath());
   if (debug_seede) args.add("-D");
   if (trace_seede) args.add("-T");
   if (seede_timeout > 0) {
      args.add("-timeout");
      args.add(Long.toString(seede_timeout));
    }
   
   for (int i = 0; i < 100; ++i) {
      Element rslt = diad_control.sendSeedeMessage(null,"PING",null,null);
      if (rslt != null) {
         synchronized (this) {
            notifyAll();
          }
         break;
       }
      if (i == 0) {
         try {
            // make IGNORE_OUTPUT to clean up output
            exec = new IvyExec(args,null,IvyExec.ERROR_OUTPUT);     
            isnew = true;
            IvyLog.logD("DITEST","Run " + exec.getCommand());
          }
         catch (IOException e) {
            break;
          }
       }
      else {
         try {
            if (exec != null) {
               int sts = exec.exitValue();
               IvyLog.logD("DITEST","Seede server disappeared with status " + sts);
               break;
             }
          }
         catch (IllegalThreadStateException e) { }
       }
      synchronized (this) {
         try {
            wait(2000);
          }
         catch (InterruptedException e) { }
       }
    }
   
   seede_starting = false;
   
   return isnew;
}



/********************************************************************************/
/*                                                                              */
/*      Setup test execution                                                    */
/*                                                                              */
/********************************************************************************/

public LaunchData setupTest(String project,String launch,int contct)
{
   LaunchData ld = startLaunch(project,launch);
   for (int i = 0; i < contct; ++i) {
      continueLaunch(project,ld);
    }
   
   startFait();
   startSeede();
   
   return ld;
}


private LaunchData startLaunch(String proj,String name)
{
   stopped_thread = null;
   
   CommandArgs args = new CommandArgs("NAME",name,
         "MODE","debug","BUILD","TRUE",
         "PROJECT",proj,
	 "REGISTER","TRUE");
   Element xml = diad_control.sendBubblesMessage("START",args,null);
   Element ldata = IvyXml.getChild(xml,"LAUNCH");
   Assert.assertNotNull(ldata);
   String launchid = IvyXml.getAttrString(ldata,"ID");
   Assert.assertNotNull(launchid);
   String targetid = IvyXml.getAttrString(ldata,"TARGET");
   Assert.assertNotNull(targetid);
   String processid = IvyXml.getAttrString(ldata,"PROCESS");
   Assert.assertNotNull(processid);
   String threadid = waitForStop();
   Assert.assertNotNull(threadid);
   
   return new LaunchData(launchid,targetid,processid,threadid);
}


private void continueLaunch(String project,LaunchData ld)
{
   stopped_thread = null;
   
   CommandArgs args = new CommandArgs("LAUNCH",ld.getLaunchId(),
	 "TARGET",ld.getTargetId(),
         "PROJECT",project,
	 "PROCESS",ld.getProcessId(),"ACTION","RESUME");
   Element xml = diad_control.sendBubblesMessage("DEBUGACTION",args,null);
   Assert.assertNotNull(xml);
   String threadid = waitForStop();
   Assert.assertNotNull(threadid);
   
   ld.setThreadId(threadid);
}


private String waitForStop()
{
   synchronized (this) {
      for (int i = 0; i < 100; ++i) {
         if (stopped_thread != null) break;
	 try {
	    wait(1000);
	  }
	 catch (InterruptedException e) { }
       }
      return stopped_thread;
    }
}


private static class LaunchData {
   
   private String lanuch_id;
   private String target_id;
   private String process_id;
   private String thread_id;
   
   LaunchData(String launch,String target,String process,String thread) {
      lanuch_id = launch;
      target_id = target;
      process_id = process;
      thread_id = thread;
    }
   
   String getLaunchId() 			{ return lanuch_id; }
   String getTargetId() 			{ return target_id; }
   String getProcessId()			{ return process_id; }
   
   @SuppressWarnings("unused")
   String getThreadId() 			{ return thread_id; }
   
   void setThreadId(String id)			{ thread_id = id; }
   
}	// end of inner class LaunchData


/********************************************************************************/
/*                                                                              */
/*      Handle run events                                                      */
/*                                                                              */
/********************************************************************************/

public void handleRunEvent(Element re)
{
   RunEventType ret = IvyXml.getAttrEnum(re,"TYPE",RunEventType.NONE);
   if (ret != RunEventType.THREAD) return;
   Element thread = IvyXml.getChild(re,"THREAD");
   if (thread == null) return;
   String kind = IvyXml.getAttrString(re,"KIND");
   switch (kind) {
      case "SUSPEND" :
	 synchronized (this) {
	    stopped_thread = IvyXml.getAttrString(thread,"ID");
	    notifyAll();
	  }
	 break;
    }   
}


}       // end of class DitestFactory




/* end of DitestFactory.java */

