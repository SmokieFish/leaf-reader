package com.leafreader.app;
import java.io.IOException;
import java.util.*;
import org.json.*;

/** Validate every incoming record before Store changes files, progress or caches. */
public final class BackupValidator {
 private BackupValidator(){}
 private static IOException invalid(String detail){return new IOException("备份内容无效："+detail+"，未开始恢复");}
 private static String string(JSONObject j,String key,int max)throws Exception{
  Object value=j.get(key);if(!(value instanceof String))throw invalid(key+" 需要文本");
  String text=(String)value;if(text.length()>max)throw invalid(key+" 过长");return text;
 }
 private static String string(JSONArray j,int key,int max)throws Exception{
  Object value=j.get(key);if(!(value instanceof String))throw invalid("记录需要文本");
  String text=(String)value;if(text.length()>max)throw invalid("记录过长");return text;
 }
 private static int integer(Object value)throws Exception{
  if(!(value instanceof Number))throw invalid("位置需要整数");
  double n=((Number)value).doubleValue();if(n<0||n>Integer.MAX_VALUE||n!=Math.floor(n))throw invalid("位置超出范围");return (int)n;
 }
 private static void position(int position,int count)throws Exception{if(position<0||position>=count)throw invalid("片段位置超出书籍范围");}
 private static String profile(JSONArray row)throws Exception{
  String value=string(row,1,64);if(!value.matches("[a-f0-9]{64}"))throw invalid("翻译配置编号错误");return value;
 }
 public static List<Book> validate(JSONObject root)throws Exception{
  if(integer(root.get("version"))!=1)throw invalid("不支持的版本");
  JSONArray books=root.getJSONArray("books"),translations=root.getJSONArray("translations"),history=root.getJSONArray("history");
  List<Book> result=new ArrayList<>();Map<String,Integer> sizes=new HashMap<>();
  for(int i=0;i<books.length();i++){
   JSONObject j=books.getJSONObject(i);String id=string(j,"id",128);
   if(!id.matches("[a-zA-Z0-9_-]+")||sizes.containsKey(id))throw invalid("书籍编号错误或重复");
   string(j,"title",2048);string(j,"author",2048);string(j,"format",24);
   JSONArray units=j.getJSONArray("units");if(units.length()==0)throw invalid("书籍没有文字");
   long characters=0;
   for(int u=0;u<units.length();u++){
    JSONObject unit=units.getJSONObject(u);String text=string(unit,"text",400);string(unit,"chapter",2048);
    if(text.trim().isEmpty()||!(unit.get("heading") instanceof Boolean))throw invalid("正文片段错误");
    characters+=text.length();if(characters>5000000)throw invalid("单本书超过 500 万字符");
   }
   int position=integer(j.get("position"));position(position,units.length());
   Book b=Book.fromJson(j);b.position=position;b.lastRead=j.getLong("lastRead");b.added=j.getLong("added");
   if(b.lastRead<0||b.added<0)throw invalid("日期错误");
   result.add(b);sizes.put(id,units.length());
  }
  Set<String> seen=new HashSet<>();
  for(int i=0;i<translations.length();i++){
   JSONArray row=translations.getJSONArray(i);if(row.length()!=4)throw invalid("译文记录不完整");
   String id=string(row,0,128),profile=profile(row);Integer size=sizes.get(id);
   if(size==null)throw invalid("译文引用了未知书籍");
   int unit=integer(row.get(2));position(unit,size);
   if(string(row,3,20000).trim().isEmpty()||!seen.add(id+":"+profile+":"+unit))throw invalid("译文为空或重复");
  }
  seen.clear();
  for(int i=0;i<history.length();i++){
   JSONObject h=history.getJSONObject(i);String id=string(h,"id",256),bookId=string(h,"bookId",128);string(h,"title",2048);
   Integer size=sizes.get(bookId);if(size==null||id.isEmpty()||!seen.add(id))throw invalid("历史记录错误");
   position(integer(h.get("position")),size);if(h.getLong("time")<0)throw invalid("历史日期错误");
  }
  if(root.has("terms")){
   JSONArray terms=root.getJSONArray("terms");Map<String,Integer> counts=new HashMap<>();seen.clear();
   for(int i=0;i<terms.length();i++){
    JSONArray row=terms.getJSONArray(i);if(row.length()!=4)throw invalid("译名记录不完整");
    String id=string(row,0,128),profile=profile(row),source=string(row,2,80),target=string(row,3,80);
    if(!sizes.containsKey(id)||!TranslationContext.validTerm(source,target))throw invalid("译名记录错误");
    String scope=id+":"+profile;String key=scope+":"+source.toLowerCase(Locale.ROOT);
    if(!seen.add(key))throw invalid("译名重复");
    int count=counts.containsKey(scope)?counts.get(scope)+1:1;counts.put(scope,count);
    if(count>TranslationContext.MAX_STORED_TERMS)throw invalid("本书译名数量超出上限");
   }
  }
  return result;
 }
 public static void sameContent(Book incoming,Book existing)throws Exception{
  if(incoming.units.size()!=existing.units.size())throw invalid("同编号书籍的正文不同");
  for(int i=0;i<incoming.units.size();i++){
   Core.Unit a=incoming.units.get(i),b=existing.units.get(i);
   if(!a.text.equals(b.text)||!a.chapter.equals(b.chapter)||a.heading!=b.heading)throw invalid("同编号书籍的正文不同");
  }
 }
}
