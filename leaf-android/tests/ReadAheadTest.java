import com.leafreader.app.*;
import java.util.*;

public final class ReadAheadTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 static void seconds(ReadAhead r,int n,boolean active,boolean ready){for(int i=0;i<n;i++)r.record(1000,active,ready);}
 static boolean consider(ReadAhead r,boolean active,boolean ready,float scroll){return r.consider(active,ready,scroll,false,30,30,400,400);}
 public static void main(String[] args){
  List<Core.Page> pages=Arrays.asList(new Core.Page(0,4),new Core.Page(4,7),new Core.Page(7,10),new Core.Page(10,12));
  Set<Integer> cache=new HashSet<>();ReadAhead r=new ReadAhead();
  check(ReadAhead.nextBatch(pages,0,cache,r.allowed(),3).equals(Arrays.asList(0,1,2)),"opening a book requests only a bounded current-page batch");
  cache.addAll(Arrays.asList(0,1,2));
  check(ReadAhead.nextBatch(pages,0,cache,true,3).equals(Arrays.asList(3)),"unfinished current content takes priority without mixing future text");
  cache.add(3);
  check(ReadAhead.nextBatch(pages,0,cache,false,3).isEmpty(),"current completion does not immediately start prefetch");
  check(!consider(r,true,true,0f),"scrollable page at the top never prefetches on layout");
  seconds(r,120,true,true);
  check(!consider(r,true,true,.60f),"time alone cannot prefetch a long page before the reading threshold");
  check(consider(r,true,true,.70f),"scrolling to seventy percent unlocks next part");
  check(ReadAhead.nextBatch(pages,0,cache,r.allowed(),3).equals(Arrays.asList(4,5,6)),"unlocked work is exactly the next page");
  cache.addAll(Arrays.asList(4,5,6));seconds(r,3600,true,true);
  check(ReadAhead.nextBatch(pages,0,cache,r.allowed(),3).isEmpty(),"idling after prefetch cannot cascade into later pages");
  r.reset();
  check(ReadAhead.nextBatch(pages,1,cache,r.allowed(),3).isEmpty(),"turning to a cached page resets its prefetch gate");
  check(ReadAhead.nextBatch(pages,3,cache,false,3).equals(Arrays.asList(10,11)),"jumping changes priority directly to the new location");
  check(ReadAhead.nextBatch(pages,3,new HashSet<>(Arrays.asList(10,11)),true,3).isEmpty(),"last page never requests beyond the book");
  r.reset();seconds(r,41,true,true);
  check(!consider(r,true,true,-1f),"short-page prefetch waits for estimated reading progress");
  seconds(r,1,true,true);check(consider(r,true,true,-1f),"short-page prefetch triggers at seventy percent of estimated time");
  r.reset();seconds(r,120,false,true);
  check(r.readingMillis()==0&&!consider(r,false,true,1f),"menu, dialogs and background time do not advance the reading plan");
  seconds(r,120,true,false);
  check(r.readingMillis()==0&&!consider(r,true,false,1f),"waiting for current translation never counts as reading or starts prefetch");
  r.reset();seconds(r,120,true,true);
  check(!r.consider(true,true,-1f,true,20,30,400,400),"automatic reading uses countdown rather than idle time");
  check(r.consider(true,true,-1f,true,9,30,400,400),"automatic reading prefetches with thirty percent time left");
  check(ReadAhead.leadAtMillis(1,800)==8000,"very short pages still have an eight-second minimum reading delay");
  check(ReadAhead.leadAtMillis(400,800)<ReadAhead.leadAtMillis(400,200),"reading speed setting changes prefetch timing");
  r.reset();r.record(600000,true,true);
  check(r.readingMillis()==2000,"clock jumps cannot count a long inactive period as reading");
  check(ReadAhead.nextBatch(Collections.emptyList(),0,cache,true,3).isEmpty(),"empty book plan is safe");
  System.out.println(count+" read-ahead checks passed.");
 }
}
