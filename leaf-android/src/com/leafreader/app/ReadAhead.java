package com.leafreader.app;
import java.util.*;

/** A bounded reading plan. Completing a prefetch never advances its own horizon. */
public final class ReadAhead {
 private long readingMillis;
 private boolean allowed;
 public void reset(){readingMillis=0;allowed=false;}
 public boolean allowed(){return allowed;}
 public long readingMillis(){return readingMillis;}
 public void record(long elapsedMillis,boolean activelyReading,boolean currentReady){
  // Do not count time in a dialog, in the background, or while waiting for translation.
  if(activelyReading&&currentReady)readingMillis+=Math.max(0,Math.min(2000,elapsedMillis));
 }
 public static long leadAtMillis(int displayCharacters,int charactersPerMinute){
  return Math.max(8000,Math.min(90000,Math.round(Math.max(1,displayCharacters)*60000.0/Math.max(100,charactersPerMinute)*.70)));
 }
 public boolean consider(boolean activelyReading,boolean currentReady,float scrollProgress,
                         boolean automatic,int remainingSeconds,int intervalSeconds,
                         int displayCharacters,int charactersPerMinute){
  if(allowed||!activelyReading||!currentReady)return allowed;
  if(scrollProgress>=0)allowed=scrollProgress>=.70f;
  else if(automatic)allowed=remainingSeconds<=Math.max(3,Math.round(intervalSeconds*.30f));
  else allowed=readingMillis>=leadAtMillis(displayCharacters,charactersPerMinute);
  return allowed;
 }
 public static List<Integer> pageUnits(List<Core.Page> pages,int page){
  List<Integer> out=new ArrayList<>();if(page<0||page>=pages.size())return out;
  Core.Page p=pages.get(page);for(int i=p.start;i<p.end;i++)out.add(i);return out;
 }
 public static List<Integer> nextBatch(List<Core.Page> pages,int page,Set<Integer> cached,boolean allowNext,int limit){
  if(page<0||page>=pages.size()||limit<=0)return Collections.emptyList();
  List<Integer> current=Core.missing(pageUnits(pages,page),cached,limit);
  // Never mix speculative future work into the batch needed for the visible page.
  if(!current.isEmpty())return current;
  return allowNext?Core.missing(pageUnits(pages,page+1),cached,limit):Collections.emptyList();
 }
}
