package com.leafreader.app;

/** The translation switch owns visibility; late cache updates cannot override it. */
public final class ReaderDisplay {
 public static String mode(boolean translationEnabled,String preferredLayout){
  if(!translationEnabled)return "original";
  return "bilingual".equals(preferredLayout)?"bilingual":"translation";
 }
 public static String text(boolean translationEnabled,String original,String cachedTranslation){
  return translationEnabled&&cachedTranslation!=null?cachedTranslation:original;
 }
}
