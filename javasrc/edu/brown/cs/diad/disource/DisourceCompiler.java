/********************************************************************************/
/*                                                                              */
/*              StemisourceCompiler.java                                           */
/*                                                                              */
/*      Build (and resolve) ASTs for files                                      */
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



package edu.brown.cs.diad.disource;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.diad.dicore.DiadLocation;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.jcode.JcodeFactory;
import edu.brown.cs.ivy.jcomp.JcompAst;
import edu.brown.cs.ivy.jcomp.JcompControl;
import edu.brown.cs.ivy.jcomp.JcompProject;
import edu.brown.cs.ivy.jcomp.JcompSemantics;
import edu.brown.cs.ivy.jcomp.JcompSource;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

class DisourceCompiler implements DisourceConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DisourceFactory source_factory;
private Map<SourceFile,JcompProject> project_map;
private Map<String,JcodeFactory> binary_map;
private Map<File,SourceFile>	file_map;
private JcompControl		jcomp_control;


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

DisourceCompiler(DicontrolMain sm,DisourceFactory fac)
{
   diad_control = sm;
   source_factory = fac;
   file_map = new HashMap<>();
   project_map = new HashMap<>();
   binary_map = new HashMap<>();
   jcomp_control = new JcompControl();
}



/********************************************************************************/
/*										*/
/*	Processing methods							*/
/*										*/
/********************************************************************************/

void compileAll(String proj,Collection<File> use)
{
   List<JcompSource> files = new ArrayList<>();
   for (File f : use) {
      SourceFile sf = getSourceFile(f);
      if (proj == null) {
	 proj = source_factory.getProjectForFile(f);
       }
      files.add(sf);
    }
   addRelatedSources(files);
   JcodeFactory jf = getJcodeFactory(proj);
   JcompProject jp = jcomp_control.getProject(jf,files);
   for (JcompSource js : files) {
      SourceFile sf = (SourceFile) js;
      project_map.put(sf,jp);
    }
}


CompilationUnit compileSource(DiadLocation loc,String source) 
{
   SourceFile sf = getSourceFile(loc.getFile());
   JcompProject jproj = getJcompProject(loc.getProject(),sf);
   DummySource dsrc = new DummySource("TestFile.java",source);
   jproj.addSourceFile(dsrc);
   jproj.resolve();
   for (JcompSemantics js : jproj.getSources()) {
      if (js.getFile() == dsrc) {
         return (CompilationUnit) js.getAstNode();
       }
    }
   
   return null;
}



ASTNode getSourceNode(String proj,File f,int offset,int line,boolean resolve)
{
   SourceFile sf = getSourceFile(f);
   CompilationUnit cu = getAstForFile(proj,sf,resolve);
   if (cu == null) return null;
   
   if (offset <= 0 && line <= 0) return cu;
   if (offset < 0) {
      offset = getLineOffset(cu,sf,line);
    }
   
   ASTNode n = findNode(cu,offset);
   
   return n;
}


ASTNode getNewSourceNode(String proj,File f,int line,int col)
{
   SourceFile sf = getSourceFile(f);
   CompilationUnit cu = JcompAst.parseSourceFile(sf.getFileContents());
   if (cu == null) return null;
   int offset = cu.getPosition(line,col);
   
   ASTNode n = findNode(cu,offset);
   
   return n;
}



static ASTNode getStatementOfNode(ASTNode node)
{
   while (node != null) {
      if (node instanceof Statement) break;
      if (node instanceof FieldDeclaration) break;
      if (node instanceof MethodDeclaration) {
         node = ((MethodDeclaration) node).getBody();
         break;
       }
      node = node.getParent();
    }
   
   return node;
}


String getSourceContents(File f)
{
   SourceFile sf = getSourceFile(f);
   return sf.getFileContents();
}


/********************************************************************************/
/*										*/
/*	Handle getting Jcode factory for project				*/
/*										*/
/********************************************************************************/

private JcodeFactory getJcodeFactory(String proj)
{
   JcodeFactory jf = binary_map.get(proj);
   if (jf != null) return jf;
   
   CommandArgs cargs = new CommandArgs("PATHS",true,"PROJECT",proj);
   Element pxml = diad_control.sendBubblesMessage("OPENPROJECT",cargs,null);
   Element cp = IvyXml.getChild(pxml,"CLASSPATH");
   List<File> sourcepaths = new ArrayList<>();
   List<String> classpaths = new ArrayList<>();
   String ignore = null;
   for (Element rpe : IvyXml.children(cp,"PATH")) {
      String bn = null;
      String ptyp = IvyXml.getAttrString(rpe,"TYPE");
      if (ptyp != null && ptyp.equals("SOURCE")) {
	 bn = IvyXml.getTextElement(rpe,"OUTPUT");
	 String sdir = IvyXml.getTextElement(rpe,"SOURCE");
	 if (sdir != null) {
	    File sdirf = new File(sdir);
	    sourcepaths.add(sdirf);
	  }
       }
      else {
	 bn = IvyXml.getTextElement(rpe,"BINARY");
       }
      if (bn == null) continue;
      if (bn.endsWith("/lib/rt.jar")) {
	 int idx = bn.lastIndexOf("rt.jar");
	 ignore = bn.substring(0,idx);
       }
      if (bn.endsWith("/lib/jrt-fs.jar")) {
	 int idx = bn.lastIndexOf("/lib/jrt-fs.jar");
	 ignore = bn.substring(0,idx);
       }
      if (IvyXml.getAttrBool(rpe,"SYSTEM")) continue;
      if (!classpaths.contains(bn)) {
	 classpaths.add(bn);
       }
    }
   if (ignore != null) {
      for (Iterator<String> it = classpaths.iterator(); it.hasNext(); ) {
	 String nm = it.next();
	 if (nm.startsWith(ignore)) it.remove();
       }
    }
   
   int ct = Runtime.getRuntime().availableProcessors();
   ct = Math.max(1,ct/2);
   jf = new JcodeFactory(ct);
   
   for (String s : classpaths) {
      jf.addToClassPath(s);
    }
   jf.load();
   
   synchronized (this) {
      JcodeFactory njf = binary_map.putIfAbsent(proj,jf);
      if (njf != null) jf = njf;
    }
   
   return jf;
}


/********************************************************************************/
/*										*/
/*	Handle getting file information 					*/
/*										*/
/********************************************************************************/

private synchronized SourceFile getSourceFile(File f)
{
   SourceFile sf = file_map.get(f);
   if (sf == null) {
      File f1 = IvyFile.getCanonical(f);
      sf = file_map.get(f1);
    }
   if (sf == null) {
      sf = new SourceFile(f);
      file_map.put(f,sf);
      File f1 = IvyFile.getCanonical(f);
      file_map.put(f1,sf);
    }
   
   return sf;
}



/********************************************************************************/
/*										*/
/*	Handle getting AST for file						*/
/*										*/
/********************************************************************************/

private CompilationUnit getAstForFile(String proj,SourceFile file,boolean resolve)
{
   JcompProject jproj = getJcompProject(proj,file);
   
   if (resolve && !jproj.isResolved()) {
      jproj.resolve();
    }
   
   for (JcompSemantics js : jproj.getSources()) {
      if (js.getFile() == file) {
	 return (CompilationUnit) js.getAstNode();
       }
    }
   
   return null;
}




private JcompProject getJcompProject(String proj,SourceFile file)
{
   JcompProject jp = project_map.get(file);
   if (jp != null) return jp;
   
   JcodeFactory jf = getJcodeFactory(proj);
   List<JcompSource> srcs = new ArrayList<>();
   srcs.add(file);
   addRelatedSources(srcs);
   jp = jcomp_control.getProject(jf,srcs);
   if (jp == null) return null;
   
   synchronized (this) {
      JcompProject njp = project_map.putIfAbsent(file,jp);
      if (njp != null) jp = njp;
      for (JcompSource src : srcs) {
	 project_map.putIfAbsent((SourceFile) src,njp);
       }
    }
   
   return jp;
}




/********************************************************************************/
/*										*/
/*	Find node for given offset\\\						*/
/*										*/
/********************************************************************************/

private int getLineOffset(CompilationUnit cu,SourceFile sf,int line)
{
   if (line <= 0) return 0;
   String text = sf.getFileContents();
   if (text == null) return 0;
   int off = cu.getPosition(line,0);
   while (off < text.length()) {
      char c = text.charAt(off);
      if (!Character.isWhitespace(c)) break;
      ++off;
    }
   return off;
}

private ASTNode findNode(CompilationUnit cu,int offset)
{
   if (cu == null) return null;
   
   ASTNode node = JcompAst.findNodeAtOffset(cu,offset); 
   
   return node;
}




/********************************************************************************/
/*										*/
/*	Augment sources as needed						*/
/*										*/
/********************************************************************************/

private void addRelatedSources(List<JcompSource> srcs)
{
   Set<String> used = new HashSet<>();
   for (JcompSource jcs : srcs) {
      used.add(jcs.getFileName());
    }
   
   List<JcompSource> add = new ArrayList<>();
   for (JcompSource jcs : srcs) {
      SourceFile sf = (SourceFile) jcs;
      File f = sf.getFile();
      File dir = f.getParentFile();
      for (File srcf : dir.listFiles()) {
	 if (srcf.getName().endsWith(".java")) {
	    if (used.contains(srcf.getPath())) continue;
	    SourceFile sf1 = getSourceFile(srcf);
	    used.add(sf1.getFileName());
	    add.add(sf1);
	  }
       }
    }
   
   srcs.addAll(add);
}

/********************************************************************************/
/*										*/
/*	File representation							*/
/*										*/
/********************************************************************************/

private static class SourceFile implements JcompSource {
   
   private File for_file;
   private String file_body;
   
   
   SourceFile(File f) {
      for_file = f;
      file_body = null;
    }
   
   File getFile()				{ return for_file; }
   
   @Override public String getFileName()	{ return for_file.getPath(); }
   
   @Override public String getFileContents() {
      if (file_body != null) return file_body;
      try {
         file_body = IvyFile.loadFile(for_file);
         return file_body;
       }
      catch (IOException e) { }
      return null;
    }
   
}	// end of inner class SourceFile



private static class DummySource implements JcompSource {

private String source_name;
private String source_cnts;

DummySource(String nm,String cnts) {
   source_name = nm;
   source_cnts = cnts;
}

@Override public String getFileContents()            { return source_cnts; }
@Override public String getFileName()                { return source_name; }


}


}       // end of class DisourceCompiler




/* end of DisourceCompiler.java */

