/********************************************************************************/
/*                                                                              */
/*              DiadTextEdit.java                                               */
/*                                                                              */
/*      Information about a text edit for patching                              */
/*                                                                              */
/********************************************************************************/
/*      Copyright 2025 Brown University -- Steven P. Reiss                    */
/*********************************************************************************
 *  Copyright 2025, Brown University, Providence, RI.                            *
 *                                                                               *
 *                        All Rights Reserved                                    *
 *                                                                               *
 *  Permission to use, copy, modify, and distribute this software and its        *
 *  documentation for any purpose other than its incorporation into a            *
 *  commercial product is hereby granted without fee, provided that the          *
 *  above copyright notice appear in all copies and that both that               *
 *  copyright notice and this permission notice appear in supporting             *
 *  documentation, and that the name of Brown University not be used in          *
 *  advertising or publicity pertaining to distribution of the software          *
 *  without specific, written prior permission.                                  *
 *                                                                               *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS                *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND            *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY      *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY          *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,              *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS               *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE          *
 *  OF THIS SOFTWARE.                                                            *
 *                                                                               *
 ********************************************************************************/



package edu.brown.cs.diad.dicore;



public interface DiadTextEdit extends Comparable<DiadTextEdit>
{

String getFileName();
int getStartOffset();
int getLength();
String getReplace();
int getEditNumber();

int getStartLine();
int getAddCount();
int getDeleteCount();

@Override default int compareTo(DiadTextEdit ed) 
{
   int v = getFileName().compareTo(ed.getFileName());
   if (v != 0) return v;
   v = ed.getStartOffset() - getStartOffset();
   if (v != 0) return v;
   v = ed.getEditNumber() - getEditNumber();
   return v;
}


}       // end of interface DiadTextEdit




/* end of DiadTextEdit.java */

