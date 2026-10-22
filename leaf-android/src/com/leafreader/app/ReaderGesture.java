package com.leafreader.app;

/** Touch classification shared by the Activity and JVM regression tests. */
public final class ReaderGesture {
 public static final int NONE=0,PREVIOUS=1,NEXT=2,MENU=3;
 private float startX,startY,width,slop,swipeDistance,maxX,maxY;
 private long started,longPress;
 private boolean active,vertical;

 public void down(float x,float y,float width,float slop,float swipeDistance,long time,long longPress){
  this.startX=x;this.startY=y;this.width=width;this.slop=slop;
  this.swipeDistance=swipeDistance;this.started=time;this.longPress=longPress;
  maxX=0;maxY=0;vertical=false;active=true;
 }
 public void move(float x,float y){
  if(!active)return;
  float dx=Math.abs(x-startX),dy=Math.abs(y-startY);
  maxX=Math.max(maxX,dx);maxY=Math.max(maxY,dy);
  // Once a vertical scroll starts, releasing it must never turn a page.
  if(dy>slop&&dy>dx*1.2f)vertical=true;
 }
 public void cancel(){active=false;}
 public int up(float x,float y,long time){
  if(!active)return NONE;
  move(x,y);active=false;
  float dx=x-startX,dy=y-startY;
  if(vertical)return NONE;
  if(Math.abs(dx)>=swipeDistance&&Math.abs(dx)>Math.abs(dy)*1.6f)return dx<0?NEXT:PREVIOUS;
  if(time-started>=longPress||maxX>slop||maxY>slop||x<0||x>width)return NONE;
  if(x<width/3f)return PREVIOUS;
  if(x>width*2f/3f)return NEXT;
  return MENU;
 }
}
