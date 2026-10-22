import com.leafreader.app.*;
import java.util.*;
import org.json.*;

public final class TranslationTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 interface Throwing {void run()throws Exception;}
 static void rejects(Throwing f,String name)throws Exception{boolean failed=false;try{f.run();}catch(Exception expected){failed=true;}check(failed,name);}
 static String repeat(String s,int n){StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(s);return b.toString();}
 static JSONObject response(List<Integer> ids,List<String> values,JSONArray terms,String finish)throws Exception{
  JSONArray translations=new JSONArray();for(int i=0;i<ids.size();i++)translations.put(new JSONObject().put("id",ids.get(i)).put("text",values.get(i)));
  JSONObject result=new JSONObject().put("translations",translations);if(terms!=null)result.put("terms",terms);
  return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason",finish).put("message",new JSONObject().put("content",result.toString()))));
 }
 static JSONObject term(String source,String target)throws Exception{return new JSONObject().put("source",source).put("target",target);}
 public static void main(String[] args)throws Exception{
  Book book=new Book();book.id="book-a";book.title="A small journey";
  for(int i=0;i<9;i++)book.units.add(new Core.Unit("source "+i,"chapter",false));
  book.units.set(2,new Core.Unit("Rusty reached the shore.","chapter",false));
  book.units.set(4,new Core.Unit("Rusty met Ravenpaw near ThunderClan.","chapter",false));
  book.units.set(5,new Core.Unit("Ravenpaw waved.","chapter",false));
  List<Integer> ids=Arrays.asList(4,5);List<String> source=Arrays.asList(book.units.get(4).text,book.units.get(5).text);
  Map<Integer,String> cached=new HashMap<>();cached.put(0,"must never be sent");cached.put(2,"小锈来到岸边。");cached.put(3,repeat("文",900));
  Map<String,String> memory=new LinkedHashMap<>();memory.put("Rusty","小锈");memory.put("Unrelated","无关");memory.put("Rust","锈");
  TranslationContext context=TranslationContext.create(book,ids,cached,memory);JSONObject json=context.toJson();
  check(json.getJSONArray("previous").length()==2&&json.getJSONArray("previous").getJSONObject(0).getString("source").contains("shore"),"request includes only two immediate previous fragments");
  check(json.getJSONArray("previous").getJSONObject(0).getString("translation").equals("小锈来到岸边。")&&!json.toString().contains("must never be sent"),"previous translations accompany source without sending distant cached content");
  check(json.getJSONArray("previous").getJSONObject(1).getString("translation").length()==700,"cached context length is bounded");
  check(json.getString("following_source").equals("source 6"),"one short following fragment is available for disambiguation");
  check(json.getJSONArray("remembered_terms").length()==1&&json.getJSONArray("remembered_terms").getJSONObject(0).getString("source").equals("Rusty"),"only relevant whole names enter the prompt; Rust does not match Rusty");
  cached.put(2,"changed");memory.put("Rusty","changed");
  check(context.toJson().toString().equals(json.toString()),"request context is an immutable snapshot");
  JSONObject start=TranslationContext.create(book,Arrays.asList(0),cached,memory).toJson();
  JSONObject end=TranslationContext.create(book,Arrays.asList(8),cached,memory).toJson();
  check(start.getJSONArray("previous").length()==0&&!end.has("following_source"),"book boundaries do not leak or overrun context");
  check(!end.getJSONArray("previous").getJSONObject(0).has("translation"),"jumping to an uncached location uses source without fabricating prior translations");
  Map<String,String> many=new LinkedHashMap<>();StringBuilder names=new StringBuilder();
  for(int i=0;i<40;i++){many.put("N"+i,"名"+i);names.append("N").append(i).append(' ');}
  Book manyBook=new Book();manyBook.title="Names";manyBook.units.add(new Core.Unit(names.toString(),"chapter",false));
  check(TranslationContext.create(manyBook,Arrays.asList(0),Collections.emptyMap(),many).toJson().getJSONArray("remembered_terms").length()==24,"at most twenty-four relevant name pairs are sent");
  check(TranslationContext.containsTerm("RUSTY's trail","Rusty")&&!TranslationContext.containsTerm("Trusty","Rusty")&&!TranslationContext.containsTerm("Rustys","Rusty"),"name matching handles case and possessives without substring collisions");
  check(TranslationContext.clip("a😀b",2).equals("a"),"bounded context never splits a surrogate pair");
  Translator.Config config=new Translator.Config();config.glossary="ThunderClan = 雷族";config.key="not-a-real-key";
  JSONObject payload=Translator.payload(config,ids,source,context);
  JSONObject user=new JSONObject(payload.getJSONArray("messages").getJSONObject(1).getString("content"));
  check(user.getString("user_glossary").equals(config.glossary)&&payload.getJSONArray("messages").getJSONObject(0).getString("content").contains("user_glossary is authoritative"),"manual glossary is supplied with explicit highest priority");
  check(user.getJSONArray("texts").length()==2&&user.getJSONArray("texts").getJSONObject(0).getInt("id")==4&&user.has("context"),"context is separate from the only fragments requested for translation");
  check(payload.getDouble("temperature")==0.2&&payload.getJSONObject("thinking").getString("type").equals("disabled")&&payload.getJSONObject("response_format").getString("type").equals("json_object"),"non-thinking JSON request uses low variation setting");
  check(!payload.toString().contains(config.key),"API credential is absent from model message payload");
  String profile=config.profile();config.key="another-key";Translator.payload(config,ids,source,context);
  check(profile.equals(config.profile()),"context preparation and API-key changes do not invalidate paid translation caches");
  config.glossary="ThunderClan = 雷部落";check(!profile.equals(config.profile()),"manual term correction selects a new translation profile");
  List<String> translated=Arrays.asList("小锈在雷族附近遇见乌爪。","乌爪挥了挥手。");
  Map<String,String> established=Collections.singletonMap("Rusty","小锈");
  JSONArray learned=new JSONArray().put(term("Rusty","小锈")).put(term("Ravenpaw","乌爪")).put(term("ThunderClan","雷族"));
  Translator.Result good=Translator.parse(response(ids,translated,learned,"stop"),ids,source,established);
  check(good.translations.equals(translated)&&good.terms.size()==2&&good.terms.get("Ravenpaw").equals("乌爪"),"valid translations and new names are accepted together");
  JSONArray bad=new JSONArray().put(term("rusty","错误译名")).put(term("OtherName","乌爪")).put(term("Ravenpaw","从未使用的名称")).put(term("Ravenpaw\n","乌爪")).put(term("Rust","小锈"));
  check(Translator.parse(response(ids,translated,bad,"stop"),ids,source,established).terms.isEmpty(),"invalid, absent or conflicting learned names are ignored");
  JSONArray duplicates=new JSONArray().put(term("Ravenpaw","乌爪")).put(term("RAVENPAW","乌爪"));
  check(Translator.parse(response(ids,translated,duplicates,"stop"),ids,source,established).terms.size()==1,"case variants cannot add duplicate learned mappings");
  check(Translator.parse(response(ids,translated,null,"stop"),ids,source,established).terms.isEmpty(),"optional missing term suggestions do not discard valid paid translations");
  JSONArray lots=new JSONArray();List<String> manyTarget=Arrays.asList(repeat("名字 ",20));
  for(int i=0;i<20;i++)lots.put(term("N"+i,"名字"));
  check(Translator.parse(response(Arrays.asList(0),manyTarget,lots,"stop"),Arrays.asList(0),Arrays.asList(names.toString()),Collections.emptyMap()).terms.size()==8,"learned names per request are capped at eight");
  rejects(()->Translator.parse(response(ids,translated,learned,"length"),ids,source,established),"truncated output cannot enter translation or name caches");
  rejects(()->Translator.parse(response(Arrays.asList(5,4),translated,learned,"stop"),ids,source,established),"out-of-order output IDs cannot be attached to the wrong source");
  rejects(()->Translator.parse(response(Arrays.asList(4),Arrays.asList("内容"),learned,"stop"),ids,source,established),"missing output fragments reject the whole batch");
  rejects(()->Translator.parse(response(ids,Arrays.asList(" ","内容"),learned,"stop"),ids,source,established),"empty output cannot silently replace source");
  rejects(()->Translator.payload(config,Arrays.asList(4,4),source,context),"duplicate input IDs are rejected");
  System.out.println(count+" translation-context checks passed.");
 }
}
