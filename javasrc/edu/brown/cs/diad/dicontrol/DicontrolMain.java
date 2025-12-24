/********************************************************************************/
/*										*/
/*		DicontrolMain.java						*/
/*										*/
/*	General Rewriting AI-base Nucleus core main program			*/
/*										*/
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 *  Permission to use, copy, modify, and distribute this software and its	 *
 *  documentation for any purpose other than its incorporation into a		 *
 *  commercial product is hereby granted without fee, provided that the 	 *
 *  above copyright notice appear in all copies and that both that		 *
 *  copyright notice and this permission notice appear in supporting		 *
 *  documentation, and that the name of Brown University not be used in 	 *
 *  advertising or publicity pertaining to distribution of the software 	 *
 *  without specific, written prior permission. 				 *
 *										 *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS		 *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND		 *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY	 *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY 	 *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,		 *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS		 *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE 	 *
 *  OF THIS SOFTWARE.								 *
 *										 *
 ********************************************************************************/


package edu.brown.cs.diad.dicontrol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dianalysis.DianalysisManager;
import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.diad.dicore.DiadRuntimeCallback;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.diexecute.DiexecuteManager;
import edu.brown.cs.diad.diruntime.DiruntimeManager;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.diad.ditest.DitestFactory;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlReader;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

public final class DicontrolMain implements DicontrolConstants
{


/********************************************************************************/
/*										*/
/*	Main program								*/
/*										*/
/********************************************************************************/

public static void main(String [] args)
{
   DicontrolMain lm = new DicontrolMain(args);
   lm.process();
}



/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private String mint_id;
private IvyLog.LogLevel log_level;
private boolean log_stderr;
private File log_file;
private DicontrolMonitor dicontrol_monitor;
private File input_file;
private boolean server_mode;
private DiruntimeManager run_manager;
private Map<DiadThread,DicontrolCandidate> debug_candidates;
private DisourceManager source_factory;
private DitestFactory test_factory;
private DianalysisManager analysis_manager;
private DiexecuteManager execute_manager;
private Properties  diad_properties;
 


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private DicontrolMain(String [] args)
{
   mint_id = null;
   log_level = IvyLog.LogLevel.INFO;
   log_stderr = false;

   String home = System.getProperty("user.home");
   File f1 = new File(home);
   log_file = new File(f1,"diad.log");
   File f2 = new File(f1,".bubbles");
   File f3 = new File(f2,"Diad.props");
   diad_properties = new Properties();
   try {
      InputStream ins = getClass().getClassLoader().getResourceAsStream("Diad.properties");
      if (ins != null) diad_properties.loadFromXML(ins);
    }
   catch (IOException e) {
      IvyLog.logE("DICONTROL","Problem loading system properties");
    }
   try (InputStream ins1 = new FileInputStream(f3)) {
      diad_properties.loadFromXML(ins1);
    }
   catch (FileNotFoundException e) { }
   catch (IOException e) {
      IvyLog.logE("DICONTROL","Problem loading user properties");
    }
   
   input_file = null;
   server_mode = false;

   dicontrol_monitor = null;
   run_manager = new DiruntimeManager(this);  
   
   debug_candidates = new HashMap<>();
   run_manager.addRuntimeListener(new RuntimeCallback());
   
   source_factory = null;
   analysis_manager = null;
   execute_manager = null;
   
   scanArgs(args);
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

DicontrolMonitor getMessageServer()             { return dicontrol_monitor; }

DiruntimeManager getRunManager()                { return run_manager; }

public DisourceManager getSourceManager()       { return source_factory; }

DianalysisManager getAnalysisManager()          { return analysis_manager; }

DiexecuteManager getExecuteManager()            { return execute_manager; }

DitestFactory getTestManager()
{
   if (test_factory ==  null) {
      test_factory = new DitestFactory(this);
    }
   
   return test_factory;
}

DitestFactory checkTestManager()
{
   return test_factory;
}

public String getMintId()                       { return mint_id; 
}

public void setupMessageServer(String mintid)
{
   mint_id = mintid;
   dicontrol_monitor = new DicontrolMonitor(this,mint_id);
  
}

public void bubblesReady()
{
   source_factory = new DisourceManager(this);
   analysis_manager = new DianalysisManager(this);  
   execute_manager = new DiexecuteManager(this);
}


public String getProperty(String id)
{
   return diad_properties.getProperty(id);
}


@SuppressWarnings("unchecked")
public <T extends Enum<T>> T getProperty(String id,T dflt)
{
   Enum<?> v = dflt;
   String s = getProperty(id);
   if (s == null || s.isEmpty()) return dflt;
   Enum<?> [] vals = dflt.getClass().getEnumConstants();
   if (vals == null) return null;
   for (Enum<?> v1 : vals) {
      if (v1.name().equalsIgnoreCase(s)) {
         v = v1;
         break;
       }
    }
   return (T) v;
}


public int getProperty(String id,int dflt)
{
   String s = getProperty(id);
   if (s == null || s.isEmpty()) return dflt;
   
   try {
      return Integer.parseInt(s);
    } 
   catch (NumberFormatException e) { }
   
   return dflt;
}




/********************************************************************************/
/*                                                                              */
/*      Send messages and responses                                             */
/*                                                                              */
/********************************************************************************/

public Element sendBubblesMessage(String cmd,CommandArgs args,String xml)
{
   return dicontrol_monitor.sendBubblesMessage(cmd,args,xml); 
}


public boolean pingEclipse()
{
   return dicontrol_monitor.pingEclipse();
}


public void pongEclipse()
{
   dicontrol_monitor.pongEclipse();
}

public Element sendFaitMessage(String cmd,CommandArgs args,String cnts)
{
   return dicontrol_monitor.sendFaitMessage(cmd,args,cnts); 
}


public Element sendSeedeMessage(String id,String cmd,CommandArgs args,String cnts)
{
   return dicontrol_monitor.sendSeedeMessage(id,cmd,args,cnts);
}

public Element sendDiadMessage(String cmd,CommandArgs args,String xml)
{
   return dicontrol_monitor.sendDiadMessage(cmd,args,xml); 
}

public Element waitForEvaluation(String id)
{
   return dicontrol_monitor.waitForEvaluation(id);
}



/********************************************************************************/
/*										*/
/*	Argument processing							*/
/*										*/
/********************************************************************************/

private void scanArgs(String [] args)
{
   for (int i = 0; i < args.length; ++i) {
      if (args[i].startsWith("-")) {
	 if (i+1 < args.length) {
	    if (args[i].startsWith("-m")) {                     // -m <mint id>
	       mint_id = args[++i];
               server_mode = true;
	       continue;
	     }
            else if (args[i].startsWith("-f")) {                // -f <input file>
               input_file = new File(args[++i]);
               continue;
             }
            else if (args[i].startsWith("-L")) {                // -Log <logfile>
	       log_file = new File(args[++i]);
	       continue;
	     }
	  }
	 if (args[i].startsWith("-")) {
	    if (args[i].startsWith("-D")) {                     // -DEBUG
	       log_level = IvyLog.LogLevel.DEBUG;
	       log_stderr = true;
	       // set log level
	     }
	    else badArgs();
	  }
	 else {
	    badArgs();
	  }
       }
    }
}



private void badArgs()
{
   System.err.println("DIAD: diad [-m <mint id>]");
   System.exit(1);
}



/********************************************************************************/
/*										*/
/*	Processing methods							*/
/*										*/
/********************************************************************************/

private void process()
{
   dicontrol_monitor = null;

   IvyLog.setupLogging("DIAD",true);
   IvyLog.setLogLevel(log_level);
   IvyLog.setLogFile(log_file);
   IvyLog.useStdErr(log_stderr);

   IvyLog.logD("DICONTROL","Starting server for " + mint_id);

   if (mint_id != null) {
      setupMessageServer(mint_id);
      bubblesReady();
    }
   
   if (input_file != null) {
      try (FileReader fr = new FileReader(input_file)) {
         processXmlFile(fr); 
       }
      catch (IOException e) {
         IvyLog.logE("LIMBA","Problem reading input file " + input_file,e);
       }
    }
   
   if (server_mode && mint_id != null) {
      boolean haveping = dicontrol_monitor.sendPing();
      synchronized (this) {
	 for ( ; ; ) {
	    // wait for explicit exit command
	    try {
	       wait(10000);
	     }
	    catch (InterruptedException e) { }
	    boolean chk = dicontrol_monitor.sendPing();
	    if (haveping && !chk) break;
	    else if (!haveping && chk) haveping = true;
	  }
       }
    }
}




private void processXmlFile(FileReader fr)
{
   try (IvyXmlReader xr = new IvyXmlReader(fr)) {
      for ( ; ; ) {
         String xmlstr = xr.readXml();
         if (xmlstr == null) break;
         IvyLog.logD("LIMBA","Process XML command: " + xmlstr);
         Element xml = IvyXml.convertStringToXml(xmlstr);
         try {
            DiadCommand cmd = setupDiadCommand(xml);
            if (cmd == null) continue;
            try (IvyXmlWriter xw = new IvyXmlWriter()) {
               xw.begin("RESULT");
               cmd.process(xw);
               xw.end("RESULT");
               IvyLog.logD("LIMBA","Command " + cmd.getCommandName() + ":\n");
               IvyLog.logD("LIMBA","RESULT: " + xw.toString());
             }
            catch (Throwable t) {
               IvyLog.logE("LIMBA",
                     "Problem prcessing command " + cmd.getCommandName(),t);
             }
          }
         catch (DiadException e) {
            IvyLog.logE("LIMBA","Bad command",e);
          }
       }
    }
   catch (IOException e) {}
}



/********************************************************************************/
/*                                                                              */
/*      Command methods                                                         */
/*                                                                              */
/********************************************************************************/

DiadCommand setupDiadCommand(Element xml) throws DiadException
{
   return DicontrolCommand.createCommand(this,xml);
}



/********************************************************************************/
/*                                                                              */
/*      Handle runtime callbacks                                                */
/*                                                                              */
/********************************************************************************/

private final class RuntimeCallback implements DiadRuntimeCallback {
   
@Override public void threadStateChanged(DiadThread thrd)
{
   DicontrolCandidate dc = debug_candidates.get(thrd);
   if (dc != null) {
      if (thrd.isRunning() || thrd.isTerminated()) {
         dc.terminate(); 
         debug_candidates.remove(thrd);
       }
    }
   else if (thrd.isStopped()) {
      dc = new DicontrolCandidate(DicontrolMain.this,thrd); 
      debug_candidates.put(thrd,dc);
      dc.addCandidateListener(new DicontrolUpdater(DicontrolMain.this,dc));
      dc.start(); 
    }
}
   
}       // end of inner class DiadRuntimeCallback




}	// end of class DicontrolMain




/* end of DicontrolMain.java */








