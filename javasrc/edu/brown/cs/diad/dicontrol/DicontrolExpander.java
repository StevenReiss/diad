/********************************************************************************/
/*										*/
/*		DicontrolExpander.java						*/
/*										*/
/*	Expand string with substitutes and conditionals 			*/
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


package edu.brown.cs.diad.dicontrol;


import java.util.Map;


final class DicontrolExpander implements DicontrolConstants {


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

private DicontrolExpander()
{
}


/********************************************************************************/
/*										*/
/*	Main entry point							*/
/*										*/
/********************************************************************************/

/**
 *	This method takes a template string and a map of name-value pairs and
 *	expands the template using the map.  Text from the template is copied
 *	directly to the output except for:
 *	 *) $NAME is replaced with the value associated with HANE or
 *		the empty string if there is no associated value.
 *	 *) $?NAME ..<text>.. $/ is either replaced with the empty string if
 *		NAME is not in the map or with text.
 *	 *) $!NAME..<text>..$/ is replaced with the empty string if NAME is
 *		defined in the map and with text if not.
 *	 *) $?NAME ..<text1> .. $! ..<text2>.. $/ is replaced with text1 if the
 *		NAME is defined and test2 if not.  Again variations on the sequences
 *		are allowed.
 *	 *) $$ is replaced with $
 *
 *	Note that the text regions above can have escape and insertion sequences
 *	as well.
 *
 *	In all the above, NAME can be replaced with {NAME}.  Also $/ can be
 *	replace diwth $/NAME or $/{NAME}
 *
 **/

public static String expand(String template,Map<String, String> values)
{
   if (template == null) return "";
   StringBuilder sb = new StringBuilder();
   int i = 0;

   while (i < template.length()) {
      char c = template.charAt(i);

      if (c == '$') {
	 if (i + 1 < template.length() && template.charAt(i + 1) == '$') {
	    // Handle $$ case
	    sb.append('$');
	    i += 2;
	  }
	 else {
	    // Process variable or conditional
	    i = processTag(template, i, values, sb);
	  }
       }
      else {
	 sb.append(c);
	 i++;
       }
    }

   return sb.toString();
}

private static int processTag(String template,int start,Map<String, String> values,
      StringBuilder output)
{
   int i = start + 1; // skip initial '$'

   // Check for conditional symbols
   boolean istruebranch = true;
   boolean isconditional = false;

   if (i < template.length() && template.charAt(i) == '?') {
      isconditional = true;
      i++;
    }
   else if (i < template.length() && template.charAt(i) == '!') {
      isconditional = true;
      istruebranch = false;
      i++;
    }

   // Extract Name
   String name;
   if (i < template.length() && template.charAt(i) == '{') {
      int closeBracket = template.indexOf('}', i);
      name = template.substring(i + 1, closeBracket);
      i = closeBracket + 1;
    }
   else {
      int namestart = i;
      while (i < template.length() && isTagChar(template.charAt(i))) {
	 i++;
       }
      name = template.substring(namestart, i);
    }

   boolean nameexists = values.containsKey(name);
   if (!istruebranch) nameexists = !nameexists;

   if (!isconditional) {
      // Standard variable replacement: $NAME
      output.append(values.getOrDefault(name, ""));
      return i;
    }

   // Logic for Conditionals: $?NAME or $!NAME
   // We need to find the matching $/ or $/NAME
   int contentstart = i;
   int endoftag = findClosingTag(template, i, name);
   String blockcontent = template.substring(contentstart, endoftag);

   // Check for the "else" separator $! inside this block
   int elseindex = findElseSeparator(blockcontent);

   if (elseindex != -1) {
      // Case: $?NAME ..text1.. $! ..text2.. $/
      String text1 = blockcontent.substring(0, elseindex);
      String text2 = blockcontent.substring(elseindex + 2);
      output.append(expand(nameexists ? text1 : text2, values));
    }
   else {
      // Case: $?NAME ..text.. $/  OR  $!NAME ..text.. $/
      boolean shouldshow = (template.charAt(start + 1) == '?') ? nameexists : !nameexists;
      if (shouldshow) {
	 output.append(expand(blockcontent, values));
       }
    }

   // Return index after the closing $/... sequence
   return skipClosingTag(template, endoftag, name);
}

private static int findClosingTag(String template,int start,String name)
{
   int i = start;
   int depth = 1;

   while (i < template.length()) {
      if (template.startsWith("$/", i)) {
	 depth--;
	 if (depth == 0) return i;
       }
      else if (isStartBlock(template,i)) {
	 depth++;
	 ++i;
       }
      else if (template.startsWith("$$")) {
	 ++i;
       }
      i++;
    }

   return template.length();
}

private static int skipClosingTag(String template,int closingStart,String name)
{
   int i = closingStart + 2; // skip $/
   if (i < template.length() && template.charAt(i) == '{') {
      return template.indexOf('}', i) + 1;
    }
   else if (i < template.length() && isTagChar(template.charAt(i))) {
      while (i < template.length() && isTagChar(template.charAt(i))) {
         i++;
       }
      return i;
    }

   return i;
}

private static int findElseSeparator(String block)
{
   // Finds $! that isn't nested inside another conditional
   int depth = 0;
   for (int i = 0; i < block.length() - 1; i++) {
      if (isStartBlock(block,i)) {
	 // If we find $! at depth 0, it's our separator
	 if (block.charAt(i + 1) == '!' && depth == 0) return i;
	 depth++;
       }
      else if (block.startsWith("$/", i)) {
	 depth--;
       }
    }

   return -1;
}


private static boolean isStartBlock(String template,int i)
{
   if (template.startsWith("$?", i)) {
      return true;
    }
   else if (template.startsWith("$!",i) && i+2 < template.length()) {
      char ch = template.charAt(i+2);
      if (isTagChar(ch) || ch == '{') {
	 return true;
       }
    }

   return false;
}


private static boolean isTagChar(char c)
{
   if (Character.isLetterOrDigit(c)) return true;
   if (c == '_') return true;
   
   return false;
}



} // end of class DicontrolExpander


/* end of DicontrolExpander.java */





















