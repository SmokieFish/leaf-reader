package com.leafreader.app;
import java.util.*;
import org.json.*;

/** Small, immutable request snapshot. Neither the full book nor all its cached text is sent. */
public final class TranslationContext {
 public static final int MAX_STORED_TERMS=256,MAX_REQUEST_TERMS=24,MAX_NEW_TERMS=8;
 private final String snapshot;
 private TranslationContext(JSONObject json){snapshot=json.toString();}
 public static TranslationContext empty(){return new TranslationContext(new JSONObject());}
 public JSONObject toJson()throws JSONException{return new JSONObject(snapshot);}
 public static TranslationContext create(Book book,List<Integer> ids,Map<Integer,String> cached,Map<String,String> memory)throws JSONException{
  if(ids.isEmpty())return empty();
  int first=ids.get(0),last=ids.get(ids.size()-1);
  JSONObject data=new JSONObject().put("book_title",clip(book.title,120)).put("chapter",clip(book.units.get(first).chapter,120));
  JSONArray before=new JSONArray();StringBuilder relevant=new StringBuilder();
  for(int id:ids)relevant.append(book.units.get(id).text).append('\n');
  for(int i=Math.max(0,first-2);i<first;i++){
   String source=clip(book.units.get(i).text,400);JSONObject pair=new JSONObject().put("source",source);
   if(cached.containsKey(i))pair.put("translation",clip(cached.get(i),700));
   before.put(pair);relevant.append(source).append('\n');
  }
  data.put("previous",before);
  if(last+1<book.units.size())data.put("following_source",clip(book.units.get(last+1).text,400));
  List<Map.Entry<String,String>> entries=new ArrayList<>(memory.entrySet());
  Collections.sort(entries,(a,b)->Integer.compare(b.getKey().length(),a.getKey().length()));
  JSONArray terms=new JSONArray();
  for(Map.Entry<String,String> e:entries){
   if(terms.length()>=MAX_REQUEST_TERMS)break;
   if(validTerm(e.getKey(),e.getValue())&&containsTerm(relevant.toString(),e.getKey()))
    terms.put(new JSONObject().put("source",e.getKey()).put("target",e.getValue()));
  }
  data.put("remembered_terms",terms);return new TranslationContext(data);
 }
 public static String clip(String text,int limit){
  if(text==null)return "";int end=Math.min(text.length(),limit);
  if(end>0&&end<text.length()&&Character.isHighSurrogate(text.charAt(end-1)))end--;
  return text.substring(0,end);
 }
 public static boolean validTerm(String source,String target){return validName(source)&&validName(target);}
 private static boolean validName(String s){
  if(s==null||s.trim().isEmpty()||s.length()>80||!s.equals(s.trim()))return false;
  for(int i=0;i<s.length();i++)if(Character.isISOControl(s.charAt(i)))return false;
  return true;
 }
 private static boolean word(char c){return c>='a'&&c<='z'||c>='0'&&c<='9'||c=='_';}
 public static boolean containsTerm(String text,String term){
  if(text==null||term==null||term.isEmpty())return false;
  String haystack=text.toLowerCase(Locale.ROOT),needle=term.toLowerCase(Locale.ROOT);
  for(int at=haystack.indexOf(needle);at>=0;at=haystack.indexOf(needle,at+1)){
   int end=at+needle.length();
   if((at==0||!word(needle.charAt(0))||!word(haystack.charAt(at-1)))&&
      (end==haystack.length()||!word(needle.charAt(needle.length()-1))||!word(haystack.charAt(end))))return true;
  }
  return false;
 }
 public static String remembered(Map<String,String> memory,String source){
  for(Map.Entry<String,String> e:memory.entrySet())if(e.getKey().equalsIgnoreCase(source))return e.getValue();return null;
 }
}
