package com.leafreader.app;
import java.util.*;

/** The horizon is anchored to the visible page, never to the last completed request. */
public final class ReadAhead {
 public static final int AHEAD_PAGES=2;
 private ReadAhead(){}
 public static List<Integer> pageUnits(List<Core.Page> pages,int page){
  List<Integer> out=new ArrayList<>();if(page<0||page>=pages.size())return out;
  Core.Page p=pages.get(page);for(int i=p.start;i<p.end;i++)out.add(i);return out;
 }
 public static List<Integer> nextBatch(List<Core.Page> pages,int page,Set<Integer> cached,int limit){
  if(page<0||page>=pages.size()||limit<=0)return Collections.emptyList();
  List<Integer> current=Core.missing(pageUnits(pages,page),cached,limit);
  // Never mix speculative future work into the batch needed for the visible page.
  if(!current.isEmpty())return current;
  List<Integer> ahead=new ArrayList<>();
  for(int p=page+1;p<pages.size()&&p<=page+AHEAD_PAGES;p++)ahead.addAll(pageUnits(pages,p));
  return Core.missing(ahead,cached,limit);
 }
}
