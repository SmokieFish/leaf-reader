import com.leafreader.app.*;
import java.util.*;
import org.json.*;

public final class BackupValidatorTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 interface Work{void run()throws Exception;}
 static void rejects(Work w,String name)throws Exception{boolean failed=false;try{w.run();}catch(Exception expected){failed=true;}check(failed,name);}
 static String profile(){StringBuilder s=new StringBuilder();for(int i=0;i<64;i++)s.append('a');return s.toString();}
 static JSONObject fixture()throws Exception{
  Book b=new Book();b.id="book_a";b.title="Synthetic backup";b.add("A small original test paragraph.","Chapter",false);b.add("Another original test paragraph.","Chapter",false);
  JSONObject book=b.toJson().put("position",1).put("lastRead",123).put("added",100);
  JSONArray tr=new JSONArray().put(new JSONArray().put(b.id).put(profile()).put(0).put("一段合成译文。"));
  JSONArray history=new JSONArray().put(new JSONObject().put("id","book_a:2026-01-01").put("bookId",b.id).put("title",b.title).put("position",1).put("time",123));
  return new JSONObject().put("version",1).put("books",new JSONArray().put(book)).put("translations",tr).put("history",history);
 }
 public static void main(String[] args)throws Exception{
  JSONObject original=fixture();String before=original.toString();List<Book> books=BackupValidator.validate(original);
  check(books.size()==1&&books.get(0).position==1&&books.get(0).lastRead==123&&original.toString().equals(before),"legacy backups validate without modifying the input or requiring name memory");
  JSONObject withTerms=fixture().put("terms",new JSONArray().put(new JSONArray().put("book_a").put(profile()).put("TestName").put("测试名")));
  check(BackupValidator.validate(withTerms).size()==1,"valid v1.3 name-memory backups remain compatible");
  JSONObject lateBad=fixture();lateBad.getJSONArray("translations").put(new JSONArray().put("book_a").put(profile()).put(99).put("invalid"));
  rejects(()->BackupValidator.validate(lateBad),"late malformed cache records are rejected before restore can begin");
  JSONObject fractional=fixture();fractional.getJSONArray("books").getJSONObject(0).put("position",0.5);
  rejects(()->BackupValidator.validate(fractional),"fractional reading positions cannot be silently truncated");
  JSONObject negative=fixture();negative.getJSONArray("translations").getJSONArray(0).put(2,-1);
  rejects(()->BackupValidator.validate(negative),"negative cached unit indices are rejected");
  JSONObject unknown=fixture();unknown.getJSONArray("translations").getJSONArray(0).put(0,"unlisted_book");
  rejects(()->BackupValidator.validate(unknown),"incoming records cannot target books outside their backup");
  JSONObject empty=fixture();empty.getJSONArray("translations").getJSONArray(0).put(3,"");
  rejects(()->BackupValidator.validate(empty),"empty cached translations cannot overwrite readable content");
  JSONObject duplicate=fixture();duplicate.getJSONArray("books").put(duplicate.getJSONArray("books").getJSONObject(0));
  rejects(()->BackupValidator.validate(duplicate),"duplicate book identities are rejected");
  JSONObject path=fixture();path.getJSONArray("books").getJSONObject(0).put("id","../escape");
  rejects(()->BackupValidator.validate(path),"book file names cannot escape the private book directory");
  JSONObject longUnit=fixture();char[] chars=new char[401];Arrays.fill(chars,'x');longUnit.getJSONArray("books").getJSONObject(0).getJSONArray("units").getJSONObject(0).put("text",new String(chars));
  rejects(()->BackupValidator.validate(longUnit),"restored source segments must remain translatable within the request limit");
  JSONObject badHistory=fixture();badHistory.getJSONArray("history").getJSONObject(0).put("position",2);
  rejects(()->BackupValidator.validate(badHistory),"late history errors are detected before any progress update");
  JSONObject badName=fixture().put("terms",new JSONArray().put(new JSONArray().put("book_a").put(profile()).put("name\n").put("名称")));
  rejects(()->BackupValidator.validate(badName),"malformed term names are rejected before cache writes");
  Book existing=books.get(0),incoming=BackupValidator.validate(fixture()).get(0);incoming.title="Renamed import";BackupValidator.sameContent(incoming,existing);
  check(true,"same original text remains restorable under a different display title");
  incoming.units.get(0).text="A different source with the same ID.";
  rejects(()->BackupValidator.sameContent(incoming,existing),"a forged same-ID book cannot replace progress or translations for different source text");
  System.out.println(count+" backup validation checks passed.");
 }
}
