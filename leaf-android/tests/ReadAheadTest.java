import com.leafreader.app.*;
import java.util.*;

public final class ReadAheadTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 public static void main(String[] args){
  List<Core.Page> pages=Arrays.asList(new Core.Page(0,4),new Core.Page(4,7),new Core.Page(7,10),new Core.Page(10,12),new Core.Page(12,15));
  Set<Integer> cache=new HashSet<>();
  check(ReadAhead.nextBatch(pages,0,cache,3).equals(Arrays.asList(0,1,2)),"current page is first, in a bounded batch");
  cache.addAll(Arrays.asList(0,1,2));
  check(ReadAhead.nextBatch(pages,0,cache,6).equals(Arrays.asList(3)),"unfinished visible page never mixes in speculative future text");
  cache.add(3);
  check(ReadAhead.nextBatch(pages,0,cache,6).equals(Arrays.asList(4,5,6,7,8,9)),"completion immediately starts both next pages without a time/scroll gate");
  check(ReadAhead.nextBatch(pages,0,cache,3).equals(Arrays.asList(4,5,6)),"nearer future page has priority when the batch is small");
  cache.addAll(Arrays.asList(4,5,6));
  check(ReadAhead.nextBatch(pages,0,cache,3).equals(Arrays.asList(7,8,9)),"next completion continues into the second future page");
  cache.addAll(Arrays.asList(7,8,9));
  check(ReadAhead.nextBatch(pages,0,cache,6).isEmpty(),"prefetch cannot cascade past two pages while idling");
  check(ReadAhead.nextBatch(pages,1,cache,6).equals(Arrays.asList(10,11)),"turning a page immediately tops up the moving two-page window");
  check(ReadAhead.nextBatch(pages,4,cache,3).equals(Arrays.asList(12,13,14)),"jump reprioritizes the visible page");
  Set<Integer> sparse=new HashSet<>(Arrays.asList(0,1,2,3,5,8));
  check(ReadAhead.nextBatch(pages,0,sparse,6).equals(Arrays.asList(4,6,7,9)),"cached fragments on either future page are never re-requested");
  check(ReadAhead.nextBatch(pages,4,new HashSet<>(Arrays.asList(12,13,14)),6).isEmpty(),"end of book has no speculative work");
  check(ReadAhead.nextBatch(pages,3,new HashSet<>(Arrays.asList(10,11)),6).equals(Arrays.asList(12,13,14)),"penultimate page clamps horizon to book end");
  check(ReadAhead.nextBatch(Collections.emptyList(),0,cache,6).isEmpty()&&ReadAhead.nextBatch(pages,-1,cache,6).isEmpty()&&ReadAhead.nextBatch(pages,8,cache,6).isEmpty(),"invalid or empty page plans are safe");
  check(ReadAhead.nextBatch(pages,0,sparse,0).isEmpty(),"zero request budget schedules no work");
  List<Core.Page> larger=Arrays.asList(new Core.Page(0,1),new Core.Page(1,9),new Core.Page(9,17),new Core.Page(17,20));
  Set<Integer> translated=new HashSet<>(Arrays.asList(0));int iterations=0;
  while(true){List<Integer> batch=ReadAhead.nextBatch(larger,0,translated,6);if(batch.isEmpty())break;translated.addAll(batch);iterations++;if(iterations>10)throw new AssertionError("runaway scheduling");}
  check(translated.size()==17&&!translated.contains(17),"multiple batches still stop after the two future pages");
  List<Core.Page> resized=Arrays.asList(new Core.Page(0,2),new Core.Page(2,5),new Core.Page(5,9));
  check(ReadAhead.nextBatch(resized,1,translated,6).isEmpty(),"layout changes reuse stable original-unit cache");
  System.out.println(count+" read-ahead checks passed.");
 }
}
