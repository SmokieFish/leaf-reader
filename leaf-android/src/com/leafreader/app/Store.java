package com.leafreader.app;
import android.content.*;import android.database.*;import android.database.sqlite.*;import java.util.*;import java.io.*;import org.json.*;
public final class Store extends SQLiteOpenHelper {
 public static class Entry {public String id,title,author,format;public int position,total;public long lastRead,added;}
 public static class Visit {public String id,bookId,title;public int position;public long time;}
 public static final String TERMS_SCHEMA="CREATE TABLE IF NOT EXISTS translation_terms(book TEXT,profile TEXT,source TEXT COLLATE NOCASE,target TEXT,PRIMARY KEY(book,profile,source))";
 final File folder;
 public Store(Context c){super(c,"reader.db",null,2);folder=new File(c.getFilesDir(),"books");folder.mkdirs();}
 @Override public void onCreate(SQLiteDatabase d){d.execSQL("CREATE TABLE books(id TEXT PRIMARY KEY,title TEXT,author TEXT,format TEXT,position INTEGER,total INTEGER,lastRead INTEGER,added INTEGER)");d.execSQL("CREATE TABLE translations(book TEXT,profile TEXT,unit INTEGER,value TEXT,PRIMARY KEY(book,profile,unit))");d.execSQL("CREATE TABLE history(id TEXT PRIMARY KEY,bookId TEXT,title TEXT,position INTEGER,time INTEGER)");d.execSQL(TERMS_SCHEMA);}
 @Override public void onUpgrade(SQLiteDatabase d,int a,int b){if(a<2)d.execSQL(TERMS_SCHEMA);}
 public synchronized List<Entry> list(){List<Entry> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM books ORDER BY lastRead DESC,added DESC",null)){while(c.moveToNext()){Entry e=new Entry();e.id=c.getString(0);e.title=c.getString(1);e.author=c.getString(2);e.format=c.getString(3);e.position=c.getInt(4);e.total=c.getInt(5);e.lastRead=c.getLong(6);e.added=c.getLong(7);out.add(e);}}return out;}
 public synchronized boolean exists(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM books WHERE id=?",new String[]{id})){return c.moveToFirst();}}
 public synchronized void saveNew(Book b)throws Exception{if(exists(b.id))return;File temp=new File(folder,b.id+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(b.toJson().toString().getBytes("UTF-8"));out.getFD().sync();}if(!temp.renameTo(new File(folder,b.id+".json")))throw new IOException("无法保存书籍");ContentValues v=new ContentValues();v.put("id",b.id);v.put("title",b.title);v.put("author",b.author);v.put("format",b.format);v.put("position",0);v.put("total",b.units.size());v.put("lastRead",0);v.put("added",b.added);getWritableDatabase().insertOrThrow("books",null,v);}
 public synchronized Book load(String id)throws Exception{if(!id.matches("[a-zA-Z0-9_-]+"))throw new IOException("书籍编号无效");byte[] data=Importer.read(new FileInputStream(new File(folder,id+".json")),60*1024*1024);Book b=Book.fromJson(new JSONObject(new String(data,"UTF-8")));for(Entry e:list())if(e.id.equals(id)){b.position=e.position;b.lastRead=e.lastRead;b.added=e.added;break;}return b;}
 public synchronized void position(Book b,int position){long time=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("position",position);v.put("lastRead",time);getWritableDatabase().update("books",v,"id=?",new String[]{b.id});String day=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).format(new Date(time));ContentValues h=new ContentValues();h.put("id",b.id+":"+day);h.put("bookId",b.id);h.put("title",b.title);h.put("position",position);h.put("time",time);getWritableDatabase().insertWithOnConflict("history",null,h,SQLiteDatabase.CONFLICT_REPLACE);}
 public synchronized List<Visit> history(){List<Visit> out=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM history ORDER BY time DESC LIMIT 200",null)){while(c.moveToNext()){Visit h=new Visit();h.id=c.getString(0);h.bookId=c.getString(1);h.title=c.getString(2);h.position=c.getInt(3);h.time=c.getLong(4);out.add(h);}}return out;}
 public synchronized void clearHistory(){getWritableDatabase().delete("history",null,null);}
 public synchronized Map<Integer,String> cache(String book,String profile){Map<Integer,String> out=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT unit,value FROM translations WHERE book=? AND profile=?",new String[]{book,profile})){while(c.moveToNext())out.put(c.getInt(0),c.getString(1));}return out;}
 public synchronized Map<String,String> terms(String book,String profile){
  Map<String,String> out=new LinkedHashMap<>();
  try(Cursor c=getReadableDatabase().rawQuery("SELECT source,target FROM translation_terms WHERE book=? AND profile=? ORDER BY rowid LIMIT 256",new String[]{book,profile})){
   while(c.moveToNext())out.put(c.getString(0),c.getString(1));
  }return out;
 }
 private void insertTerms(SQLiteDatabase d,String book,String profile,Map<String,String> values){
  Map<String,String> existing=terms(book,profile);
  for(Map.Entry<String,String> e:values.entrySet()){
   if(existing.size()>=TranslationContext.MAX_STORED_TERMS)break;
   if(!TranslationContext.validTerm(e.getKey(),e.getValue())||TranslationContext.remembered(existing,e.getKey())!=null)continue;
   ContentValues v=new ContentValues();v.put("book",book);v.put("profile",profile);v.put("source",e.getKey());v.put("target",e.getValue());
   d.insertWithOnConflict("translation_terms",null,v,SQLiteDatabase.CONFLICT_IGNORE);existing.put(e.getKey(),e.getValue());
  }
 }
 public synchronized void cache(String book,String profile,List<Integer> ids,List<String> texts,Map<String,String> learned){
  if(!exists(book))return;SQLiteDatabase d=getWritableDatabase();d.beginTransaction();
  try{
   for(int i=0;i<ids.size();i++){
    ContentValues v=new ContentValues();v.put("book",book);v.put("profile",profile);v.put("unit",ids.get(i));v.put("value",texts.get(i));
    d.insertWithOnConflict("translations",null,v,SQLiteDatabase.CONFLICT_REPLACE);
   }
   insertTerms(d,book,profile,learned);d.setTransactionSuccessful();
  }finally{d.endTransaction();}
 }
 public synchronized void remove(String id){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{d.delete("translation_terms","book=?",new String[]{id});d.delete("translations","book=?",new String[]{id});d.delete("history","bookId=?",new String[]{id});d.delete("books","id=?",new String[]{id});d.setTransactionSuccessful();}finally{d.endTransaction();}new File(folder,id+".json").delete();}
 public synchronized void exportTo(OutputStream stream)throws Exception{JSONObject root=new JSONObject();root.put("version",1);JSONArray books=new JSONArray();for(Entry e:list()){JSONObject j=load(e.id).toJson();j.put("position",e.position);j.put("lastRead",e.lastRead);j.put("added",e.added);books.put(j);}root.put("books",books);JSONArray t=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT book,profile,unit,value FROM translations",null)){while(c.moveToNext()){JSONArray a=new JSONArray();a.put(c.getString(0));a.put(c.getString(1));a.put(c.getInt(2));a.put(c.getString(3));t.put(a);}}root.put("translations",t);
 JSONArray names=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT book,profile,source,target FROM translation_terms ORDER BY rowid",null)){
  while(c.moveToNext()){JSONArray a=new JSONArray();for(int i=0;i<4;i++)a.put(c.getString(i));names.put(a);}
 }root.put("terms",names);JSONArray h=new JSONArray();for(Visit v:history()){JSONObject j=new JSONObject();j.put("id",v.id);j.put("bookId",v.bookId);j.put("title",v.title);j.put("position",v.position);j.put("time",v.time);h.put(j);}root.put("history",h);try(OutputStream out=stream){out.write(root.toString().getBytes("UTF-8"));}}
 public synchronized void restore(InputStream stream)throws Exception{JSONObject root=new JSONObject(new String(Importer.read(stream,65*1024*1024),"UTF-8"));List<Book> validated=BackupValidator.validate(root);for(Book b:validated)if(exists(b.id))BackupValidator.sameContent(b,load(b.id));JSONArray tr=root.getJSONArray("translations"),hist=root.getJSONArray("history");for(Book b:validated){saveNew(b);ContentValues v=new ContentValues();v.put("position",b.position);v.put("lastRead",b.lastRead);getWritableDatabase().update("books",v,"id=?",new String[]{b.id});}SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{for(int i=0;i<tr.length();i++){JSONArray a=tr.getJSONArray(i);if(!exists(a.getString(0)))continue;ContentValues v=new ContentValues();v.put("book",a.getString(0));v.put("profile",a.getString(1));v.put("unit",a.getInt(2));v.put("value",a.getString(3));d.insertWithOnConflict("translations",null,v,SQLiteDatabase.CONFLICT_REPLACE);}JSONArray names=root.optJSONArray("terms");
 if(names!=null)for(int i=0;i<names.length();i++){
  JSONArray a=names.optJSONArray(i);if(a==null||a.length()!=4||!exists(a.optString(0,"")))continue;
  String from=a.optString(2,""),to=a.optString(3,"");
  if(TranslationContext.validTerm(from,to))insertTerms(d,a.getString(0),a.getString(1),Collections.singletonMap(from,to));
 }
 for(int i=0;i<hist.length();i++){JSONObject j=hist.getJSONObject(i);if(!exists(j.getString("bookId")))continue;ContentValues v=new ContentValues();v.put("id",j.getString("id"));v.put("bookId",j.getString("bookId"));v.put("title",j.getString("title"));v.put("position",j.getInt("position"));v.put("time",j.getLong("time"));d.insertWithOnConflict("history",null,v,SQLiteDatabase.CONFLICT_REPLACE);}d.setTransactionSuccessful();}finally{d.endTransaction();}}
}
