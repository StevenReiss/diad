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
import java.io.FileReader;
import java.io.IOException;

import org.w3c.dom.Element;

import edu.brown.cs.diad.dicore.DiadException;
import edu.brown.cs.ivy.exec.IvyExec;
import edu.brown.cs.ivy.file.IvyLog;
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
   
   input_file = null;
   server_mode = false;

   dicontrol_monitor = null;

   scanArgs(args);
}



/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

DicontrolMonitor getMessageServer()             { return dicontrol_monitor; }

void setupMessageServer(String mintid)
{
   mint_id = mintid;
   dicontrol_monitor = new DicontrolMonitor(this,mint_id);
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
   return null;
}



/********************************************************************************/
/*										*/
/*	Setup bedrock for debugging						*/
/*										*/
/********************************************************************************/

private static final String BROWN_ECLIPSE = "/u/spr/java-2024-09/eclipse/eclipse";
private static final String BROWN_WS = "/u/spr/Eclipse/";
private static final String HOME_MAC_ECLIPSE =
   "/vol/Developer/java-2024-09/Eclipse.app/Contents/MacOS/eclipse";
private static final String HOME_MAC_WS = "/Users/spr/Eclipse/";
private static final String HOME_LINUX_ECLIPSE = "/pro/eclipse/java-2023-12/eclipse/eclipse";
private static final String HOME_LINUX_WS = "/home/spr/Eclipse/";

void setupBedrock(String workspace,String mint)
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

   setupMessageServer(mint);

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
	 if (dicontrol_monitor.pingEclipse()) {
	    dicontrol_monitor.sendBubblesMessage("LOGLEVEL","LEVEL='DEBUG'",null);
	    dicontrol_monitor.sendBubblesMessage("ENTER",null,null);
	    return;
	  }
	 if (i == 0) {
	    new IvyExec(cmd);
	    dicontrol_monitor.pongEclipse();
	  }
       }
    }
   catch (IOException e) {
      IvyLog.logE("LIMBA","Problem with eclipse",e);
    }

   throw new Error("Problem running Eclipse: " + cmd);
}




}	// end of class DicontrolMain




/* end of DicontrolMain.java */








