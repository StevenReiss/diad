/********************************************************************************/
/*                                                                              */
/*              DiexecuteAction.java                                            */
/*                                                                              */
/*      Action for settoing up proper execution context                         */
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



package edu.brown.cs.diad.diexecute;

import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.diad.dicore.DiadValue;
import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

abstract class DiexecuteAction implements DiexecuteConstants
{


/********************************************************************************/
/*                                                                              */
/*      Factory methods                                                         */
/*                                                                              */
/********************************************************************************/

static DiexecuteAction createSetAction(String nm,DiadValue bv)
{
   return new SetAction(nm,bv);
}



static DiexecuteAction createInitAction(String expr)
{
   return new InitAction(expr);
}



/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

protected DiexecuteAction()
{ }




/********************************************************************************/
/*                                                                              */
/*      Action methods                                                          */
/*                                                                              */
/********************************************************************************/

abstract void perform(DiexecuteManager ctrl,String session,DiadThread thrd,boolean first);


private static void sendInitialization(String init,DiexecuteManager rc,String session,DiadThread tid,boolean first) 
{
   CommandArgs args = new CommandArgs("THREAD",tid.getThreadId(),
         "REMOVE",first);
   IvyXmlWriter xw = new IvyXmlWriter();
   xw.cdataElement("EXPRESSION",init);
   String cnts = xw.toString();
   xw.close();
   rc.getDiadControl().sendSeedeMessage(session,"INITIALIZATION",
         args,cnts);
}



/********************************************************************************/
/*                                                                              */
/*      Set a variable to a value                                               */
/*                                                                              */
/********************************************************************************/

private static class SetAction extends DiexecuteAction {
   
   private String var_name;
   private DiadValue set_value;
   
   SetAction(String nm,DiadValue v) {
      var_name = nm;
      set_value = v;
    }
   
   @Override public String toString() {
      return var_name + "=" + set_value;
    }
   
   @Override void perform(DiexecuteManager rc,String session,DiadThread tid,boolean first) {
      String expr = var_name + " = " + set_value.getJavaValue();
      sendInitialization(expr,rc,session,tid,first);
//    CommandArgs args = new CommandArgs("VAR",var_name);
//    IvyXmlWriter xw = new IvyXmlWriter();
//    set_value.outputXml(xw);
//    String cnts = xw.toString();
//    xw.close();
//    rc.sendSeedeMessage(session,"SETVALUE",args,cnts);
    }
   
}       // end of inner class SetAction



/********************************************************************************/
/*                                                                              */
/*      Initialization action                                                   */
/*                                                                              */
/********************************************************************************/

private static class InitAction extends DiexecuteAction {
   
   private String init_expression;
   
   InitAction(String expr) {
      init_expression = expr;
    }
   
   @Override public String toString() {
      return init_expression;
    }
   
   @Override void perform(DiexecuteManager rc,String session,DiadThread tid,boolean first) {
      sendInitialization(init_expression,rc,session,tid,first);
    }
   
}       // end of inner class InitAction





}       // end of class DiexecuteAction




/* end of DiexecuteAction.java */

