/********************************************************************************/
/*                                                                              */
/*              DiruntimeStackTrace.java                                        */
/*                                                                              */
/*      Set up runtime for a stack trace to enable bug analysis                 */
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



package edu.brown.cs.diad.diruntime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.brown.cs.diad.dicore.DiadThread;
import edu.brown.cs.ivy.file.IvyLog;

class DiruntimeStackTrace implements DiruntimeConstants
{


/********************************************************************************/
/*                                                                              */
/*      Private Storage                                                         */
/*                                                                              */
/********************************************************************************/

private String  exception_message;

private static Pattern FRAME_PAT = Pattern.compile(
      "\s*at ([A-Za-z0-9$.]+/)?([A-Za-z0-9$.]+)\\(([A-Za-z0-9/]+\\.java)(:[0-9]+))\s*"
);


/********************************************************************************/
/*                                                                              */
/*      Constructors                                                            */
/*                                                                              */
/********************************************************************************/

DiruntimeStackTrace(String trace)
{
   // parse the trace to get a sequence of frames
   // embed those frames in a stack inside a thread
   // also extract symptom-specific informatino where available
   //           Assertions: what is wrong
   //           NullPointer: what expression is null
   //           IndexBounds: value versus bound, array
}



/********************************************************************************/
/*                                                                              */
/*      Access methoods                                                         */
/*                                                                              */
/********************************************************************************/

DiadThread getThread()
{
   return null;
}


/********************************************************************************/
/*                                                                              */
/*      Trace parsing                                                           */
/*                                                                              */
/********************************************************************************/

private void parseTrace(String trace)
{
   exception_message = null;
   
   try (BufferedReader br = new BufferedReader(new StringReader(trace))) {
      for ( ; ; ) {
         String line = br.readLine();
         if (line == null) break;
         if (line.isEmpty()) continue;
         if (exception_message == null) {
            // first line list the exception
            exception_message = line;
            continue;
          }
         Matcher m = FRAME_PAT.matcher(line);
         if (m.matches()) {
            String mod = m.group(1);
            String method = m.group(2);
            String file = m.group(4);
            String lno = m.group(5);
            IvyLog.logD("DIRUNTIME","PARSE " + mod + " " + method + " " +
                  file + " " + lno);
          }
         else {
            IvyLog.logD("DIRUNTIME","Stack frame mismatch " + line);
          }
       }
    }
   catch (IOException e) {
      IvyLog.logE("DIRUNTIME","Problem parsing trace");
    }
}

}       // end of class DiruntimeStackTrace




/* end of DiruntimeStackTrace.java */

