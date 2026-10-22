package com.leafreader.app;
import java.util.*;
public final class Core {
 public static class Unit {public String text,chapter;public boolean heading;public Unit(String t,String c,boolean h){text=t;chapter=c;heading=h;}}
 public static class Page {public int start,end;public Page(int a,int b){start=a;end=b;}}
 public interface Measure {float height(Unit unit);}
 public static List<String> split(String s,int max){List<String> out=new ArrayList<>();s=s.trim();while(s.length()>max){int cut=max;for(int i=max-1;i>=max/3;i--){char c=s.charAt(i);if(Character.isWhitespace(c)||".!?。！？；;".indexOf(c)>=0){cut=i+1;break;}}if(cut<s.length()&&Character.isHighSurrogate(s.charAt(cut-1)))cut--;out.add(s.substring(0,cut));s=s.substring(cut);}if(!s.isEmpty())out.add(s);return out;}
 public static List<Page> paginate(List<Unit> units,Measure measure,float available){List<Page> result=new ArrayList<>();int start=0;float used=0;for(int i=0;i<units.size();i++){float h=measure.height(units.get(i));if(i>start&&(used+h>available||(units.get(i).heading&&used>available*.45f))){result.add(new Page(start,i));start=i;used=0;}used+=h;}if(start<units.size())result.add(new Page(start,units.size()));return result;}
 public static int pageAt(List<Page> pages,int position){for(int i=0;i<pages.size();i++)if(position>=pages.get(i).start&&position<pages.get(i).end)return i;return Math.max(0,pages.size()-1);}
 public static List<Integer> window(List<Page> pages,int page){List<Integer> out=new ArrayList<>();for(int p=Math.max(0,page);p<Math.min(page+4,pages.size());p++)for(int i=pages.get(p).start;i<pages.get(p).end;i++)out.add(i);return out;}
 public static List<Integer> missing(List<Integer> desired,Set<Integer> cached,int limit){List<Integer> out=new ArrayList<>();for(int i:desired)if(!cached.contains(i)){out.add(i);if(out.size()==limit)break;}return out;}
}
