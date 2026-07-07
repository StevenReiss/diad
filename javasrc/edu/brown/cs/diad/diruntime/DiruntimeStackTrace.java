/********************************************************************************/
/*										*/
/*		DiruntimeStackTrace.java					*/
/*										*/
/*	Set up runtime for a stack trace to enable bug analysis 		*/
/*										*/
/********************************************************************************/
/*	Copyright 2025 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.diad.diruntime;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadLocalVariable;
import edu.brown.cs.diad.dicore.DiadStack;
import edu.brown.cs.diad.dicore.DiadStackFrame;
import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.diad.disource.DisourceManager;
import edu.brown.cs.ivy.file.IvyLog;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class DiruntimeStackTrace implements DiruntimeConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private DicontrolMain diad_control;
private UserThread    user_thread;

private static AtomicInteger frame_counter = new AtomicInteger(0);


private static final Pattern FRAME_PAT = Pattern.compile(
      "\\s*at\\s+([A-Za-z0-9$.]+/)?([A-Za-z0-9$.]+)\\(([A-Za-z0-9/]+\\.java)(:[0-9]+)\\)\\s*"
);
private static final int MOD_GROUP = 1;
private static final int METHOD_GROUP = 2;
private static final int FILE_GROUP = 3;
private static final int LINE_GROUP = 4;


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

DiruntimeStackTrace(DicontrolMain dc,String trace)
{
   diad_control = dc;
   user_thread = parseTrace(trace);
}


/********************************************************************************/
/*										*/
/*	Access methoods 							*/
/*										*/
/********************************************************************************/

DiadThread getThread()
{
   return user_thread;
}


/********************************************************************************/
/*										*/
/*	Trace parsing								*/
/*										*/
/********************************************************************************/

private UserThread parseTrace(String trace)
{
   String exc = null;
   
   trace = trace.replace((char) 0xa0,' ');
   trace = trace.replace((char) 0xc2,' ');
   
   boolean valid = false;
   List<DiadStackFrame> frames = new ArrayList<>();
   try (BufferedReader br = new BufferedReader(new StringReader(trace))) {
      for ( ; ; ) {
	 String line = br.readLine();
	 if (line == null) break;
         line = line.trim();
	 if (line.isBlank()) continue;
	 if (exc == null) {
	    exc = line;
	    continue;
	  }
	 Matcher m = FRAME_PAT.matcher(line);
	 if (m.matches()) {
	    String mod = m.group(MOD_GROUP);
	    String method = m.group(METHOD_GROUP);
	    String file = m.group(FILE_GROUP);
	    String lno = m.group(LINE_GROUP);
	    IvyLog.logD("DIRUNTIME","PARSE " + mod + " " + method + " " +
		  file + " " + lno);
	    UserFrame uf = new UserFrame(mod,method,file,lno);
	    frames.add(uf);
            if (uf.isUserFrame()) valid = true;
	  }
	 else {
	    IvyLog.logD("DIRUNTIME","Stack frame mismatch " + line);
	  }
       }
    }
   catch (IOException e) {
      IvyLog.logE("DIRUNTIME","Problem parsing trace");
    }
   
   if (!valid) return null;
   
   DiruntimeStack stack = new DiruntimeStack(frames);
   return new UserThread(stack,exc);
}



/********************************************************************************/
/*										*/
/*	Local thread								*/
/*										*/
/********************************************************************************/

private class UserThread implements DiadThread {

   private DiruntimeStack for_stack;
   private String thread_id;
   private String exception_type;
   private String exception_message;

   UserThread(DiruntimeStack stk,String exc) {
      for_stack = stk;
      thread_id = "USERTHREAD_" +  frame_counter.incrementAndGet();
      int idx = exc.indexOf(":");
      if (idx > 0) {
         exception_type = exc.substring(0,idx);
         exception_message = exc.substring(idx+1).trim();
       }
      else {
         exception_type = exc;
         exception_message = null;
       }
    }

   @Override public String getThreadName()		{ return "STACKTRACE"; }
   @Override public String getThreadId()		{ return thread_id; }
   @Override public boolean isTerminated()		{ return false; }
   @Override public boolean isStopped() 		{ return true; }
   @Override public boolean isRunning() 		{ return false; }
   @Override public DiadStack getStack()		{ return for_stack; }
   @Override public boolean isInternal()		{ return false; }
   @Override public String getExceptionType()	 { return exception_type; }
   @Override public String getExceptionDetail()         { return exception_message; }
   @Override public String getProcessId()		{ return thread_id; }

   @Override public DiadValue evaluate(String e)	{ return null; }
   @Override public DiadValue evaluate(String e,DiadStackFrame frm) {
      return null;
    }
   @Override public Map<String,DiadValue> getParameterValues(DiadStackFrame frm) {
      return null;
    }

   @Override public void outputXml(IvyXmlWriter xw) {
      xw.begin("THREAD");
      xw.field("ID",thread_id);
      xw.field("NAME",getThreadName());
      xw.field("TYPE","USER");
      xw.field("STATE","EXCEPTION");
      xw.field("DETAIL",exception_message);
      xw.field("EXCEPTIOM",exception_type);
      xw.field("FRAMECOUNT",for_stack.getFrames().size());
      xw.field("PROCESS",getProcessId());
      xw.end("THREAD");
    }

}



/********************************************************************************/
/*										*/
/*	Local stack frame							*/
/*										*/
/********************************************************************************/

private class UserFrame implements DiadStackFrame {

   private String frame_id;
   private String class_name;
   private String method_name;
   private String method_signature;
   private String format_signature;
   private int line_number;
   private File source_file;
   private boolean is_userframe;

   UserFrame(String mod,String mthd,String file,String line) {
      frame_id = "UF_" + frame_counter.incrementAndGet();
      int idx = mthd.lastIndexOf(".");
      class_name = mthd.substring(0,idx);
      method_name = mthd.substring(idx+1);
      method_signature = null;
      format_signature = null;
      source_file = null;
      idx = line.indexOf(":");
      if (idx >= 0) line = line.substring(idx+1).trim();
      line_number = Integer.parseInt(line);
      is_userframe = false;
   
      DisourceManager dm = diad_control.getSourceManager();
      Element itms = dm.findMethod(getFullMethodName(),true);
      for (Element match : IvyXml.children(itms,"MATCH")) {
         IvyLog.logD("DIRUNTIME","Work on " + IvyXml.convertXmlToString(match));
         Element mi = IvyXml.getChild(match,"ITEM");
         String fnm = IvyXml.getAttrString(match,"FILE");
         if (!fnm.endsWith(File.separator + file)) continue;
         String pnm = IvyXml.getAttrString(match,"PROJECT");
         int soff = IvyXml.getAttrInt(match,"STARTOFFSET");
         ASTNode ast = dm.getSourceNode(pnm,new File(fnm),soff,-1,false,false);
         CompilationUnit cu = (CompilationUnit) ast.getRoot();
         int isoff = IvyXml.getAttrInt(mi,"STARTOFFSET");
         int ieoff = IvyXml.getAttrInt(mi,"ENDOFFSET");
         int isline = cu.getLineNumber(isoff);
         int ieline = cu.getLineNumber(ieoff);
         if (line_number < isline && line_number > ieline) continue;
         source_file = new File(fnm);
         is_userframe = true;
         format_signature = IvyXml.getAttrString(mi,"PARAMETERS");
         method_signature = format_signature;
         break;
       }
    }

   @Override public String getFrameId() 		{ return frame_id; }
   @Override public String getClassName()		{ return class_name; }
   @Override public String getMethodName()		{ return method_name; }
   @Override public String getMethodSignature() 	{ return method_signature; }
   @Override public String getFormatSignature() 	{ return format_signature; }
   @Override public int getLineNumber() 		{ return line_number; }
   @Override public File getSourceFile()		{ return source_file; }
   @Override public boolean isUserFrame()		{ return is_userframe; }
   @Override public String getFullMethodName() {
      String rslt = getClassName();
      rslt += "." + method_name;
      if (method_signature != null) {
	 rslt += method_signature;
       }
      return rslt;
    }

   @Override public Collection<String> getLocals() {
      return new ArrayList<>();
    }
   @Override public DiadLocalVariable getLocal(String name) {
      return null;
    }

   @Override public void outputXml(IvyXmlWriter xw) {
      xw.begin("FRAME");
      xw.field("ID",frame_id);
      xw.field("CLASS",class_name);
      xw.field("METHOD",method_name);
      xw.field("LINE",line_number);
      if (source_file != null) {
	 xw.field("FILE",source_file.getPath());
       }
      xw.field("USER",is_userframe);
      xw.textElement("SIGNATURE",method_signature);
      xw.textElement("FORMATTED",format_signature);
      xw.end("FRAME");
    }

   @Override public JSONObject toJson() {
      JSONObject rslt = new JSONObject();
      rslt.put("METHOD",class_name + "." +  method_name + format_signature);
      rslt.put("LINE",line_number);

      JSONArray lcls = new JSONArray();
      rslt.put("LOCALS",lcls);

      return rslt;
    }

}	// end of inner class UserFrame


}	// end of class DiruntimeStackTrace




/* end of DiruntimeStackTrace.java */

