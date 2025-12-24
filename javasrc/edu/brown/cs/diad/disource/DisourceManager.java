/********************************************************************************/
/*                                                                              */
/*              DisourceFactory.java                                            */
/*                                                                              */
/*      Access to source files in AST form                                      */
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
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.w3c.dom.Element;

import edu.brown.cs.diad.dicontrol.DicontrolMain;
import edu.brown.cs.ivy.file.IvyFile;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXml;

public class DisourceManager implements DisourceConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private DicontrolMain   diad_control;
private DisourceCompiler the_compiler;
private Map<File,String> project_map;
private String          default_project;



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

public DisourceManager(DicontrolMain ctrl)
{
   diad_control = ctrl;
   project_map = new HashMap<>();
   default_project = null;
   the_compiler = new DisourceCompiler(ctrl,this);
   
   buildProjectMap();
}


/********************************************************************************/
/*                                                                              */
/*      Primary access methods                                                  */
/*                                                                              */
/********************************************************************************/

public ASTNode getSourceNode(String proj,File f,int offset,int line,
      boolean resolve,boolean stmt)
{
   if (proj == null && f != null) proj = getProjectForFile(f);
   
   ASTNode n = the_compiler.getSourceNode(proj,f,offset,line,resolve);
   
   if (stmt) n = DisourceCompiler.getStatementOfNode(n);
   
   return n;
}



/********************************************************************************/
/*                                                                              */
/*      Handle file-project associations                                        */
/*                                                                              */
/********************************************************************************/

public String getProjectForFile(File f)
{
   if (f == null) return default_project;
   
   String p = project_map.get(f);
   if (p == null) p = default_project;
   
   return p;
}


private void buildProjectMap()
{
   project_map = new HashMap<>();
   default_project = null;
   
   Element xml = diad_control.sendBubblesMessage("PROJECTS",null,null);
   for (Element p : IvyXml.children(xml,"PROJECT")) {
      String nm = IvyXml.getAttrString(p,"NAME");
      CommandArgs args = new CommandArgs("PROJECT",nm,"FILES",true);
      Element pxml = diad_control.sendBubblesMessage("OPENPROJECT",args,null);
      Element rxml = IvyXml.getChild(pxml,"PROJECT");
      Element files = IvyXml.getChild(rxml,"FILES");
      for (Element fxml : IvyXml.children(files,"FILE")) {
         if (IvyXml.getAttrBool(fxml,"SOURCE")) {
            String fnm = IvyXml.getAttrString(fxml,"PATH");
            File f = new File(fnm);
            project_map.putIfAbsent(f,nm);
            File f1 = IvyFile.getCanonical(f);
            if (f1 != f) project_map.putIfAbsent(f,nm);
            if (default_project == null) default_project = nm;
          }
       }
    }
}


}       // end of class DisourceFactory




/* end of DisourceFactory.java */

