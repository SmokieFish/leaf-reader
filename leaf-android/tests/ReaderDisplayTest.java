import com.leafreader.app.ReaderDisplay;
import java.util.*;

public final class ReaderDisplayTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 public static void main(String[] args){
  String original="An original paragraph.";Map<Integer,String> cache=new HashMap<>();cache.put(7,"一段译文。");
  check(ReaderDisplay.text(true,original,cache.get(7)).equals("一段译文。"),"enabled reader displays its cached translation");
  check(ReaderDisplay.text(false,original,cache.get(7)).equals(original),"switching translation off immediately restores original text");
  cache.put(7,"稍后返回的译文。");
  check(ReaderDisplay.text(false,original,cache.get(7)).equals(original),"an in-flight result arriving after switch-off cannot replace original text");
  check(ReaderDisplay.text(true,original,cache.get(7)).equals("稍后返回的译文。"),"switching back on reuses retained translation cache");
  check(ReaderDisplay.mode(false,"bilingual").equals("original"),"switch-off also hides bilingual translation rows");
  check(ReaderDisplay.mode(true,"bilingual").equals("bilingual"),"re-enabling preserves preferred bilingual layout");
  check(ReaderDisplay.mode(true,"original").equals("translation"),"legacy original-only setting cannot hide explicitly re-enabled translations");
  check(ReaderDisplay.text(true,original,null).equals(original),"untranslated content continues to show readable source text");
  System.out.println(count+" reader-display checks passed.");
 }
}
