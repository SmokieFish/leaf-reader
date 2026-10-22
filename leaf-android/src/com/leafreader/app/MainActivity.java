package com.leafreader.app;
import android.app.*;import android.os.*;import android.content.*;import android.content.res.Configuration;import android.database.Cursor;import android.graphics.*;import android.graphics.drawable.GradientDrawable;import android.net.Uri;import android.provider.OpenableColumns;import android.text.*;import android.view.*;import android.widget.*;
import java.util.*;import java.util.concurrent.*;import java.io.*;
public final class MainActivity extends Activity {
 Store store;SharedPreferences prefs;ApiKeyStore apiKeys;LinearLayout root,screen,readerBody;ScrollView readerScroll;LinearLayout readerTop,readerBottom;TextView status,pageLabel,autoButton,translateButton,quietProgress;FrameLayout readerFrame;Book book;List<Core.Page> pages=new ArrayList<>();Map<Integer,String> cached=new HashMap<>();String profile="",apiKey="",error="";boolean translate=false,blocked=false,networkBusy=false,foreground=false,automatic=false,modal=false;int remaining=30,tab=0,bg,text,muted,accent,surface;final ReaderGesture readerGesture=new ReaderGesture();boolean menuVisible=false,selectingText=false,layingOut=false;final ReadAhead readAhead=new ReadAhead();long readingGeneration=0,lastTick=0;
 final Handler ui=new Handler(Looper.getMainLooper());final ExecutorService io=Executors.newSingleThreadExecutor(),network=Executors.newSingleThreadExecutor();
 final Runnable timer=new Runnable(){public void run(){
  long now=SystemClock.elapsedRealtime(),delta=lastTick==0?0:now-lastTick;lastTick=now;
  boolean active=activelyReading();
  if(book!=null&&translate){readAhead.record(delta,active,currentComplete());considerReadAhead();}
  if(active&&automatic){
   if(!translate||currentComplete()){
    View child=readerScroll.getChildAt(0);
    if(child!=null&&readerScroll.getScrollY()+readerScroll.getHeight()<child.getHeight()-dp(5))
     readerScroll.smoothScrollBy(0,Math.max(dp(4),readerScroll.getHeight()/Math.max(5,prefs.getInt("seconds",30))));
    else if(--remaining<=0){int p=page();if(p<pages.size()-1)flip(p+1);else{automatic=false;toast("已经读到最后一页");}}
   }
  }
  if(book!=null){pump();updateStatus();}
  ui.postDelayed(this,1000);
 }};
 boolean activelyReading(){return foreground&&book!=null&&readerScroll!=null&&!modal&&!menuVisible&&!selectingText&&!layingOut;}
 void resetReadProgress(){readAhead.reset();readingGeneration++;lastTick=SystemClock.elapsedRealtime();}
 int displayedCharacters(){
  if(book==null||pages.isEmpty())return 0;
  Core.Page p=pages.get(page());int total=0;String mode=prefs.getString("view","translation");
  for(int i=p.start;i<p.end;i++){
   String original=book.units.get(i).text,value=cached.get(i);
   total+=(value==null||mode.equals("original")?original:value).length();
   if(mode.equals("bilingual")&&value!=null)total+=original.length();
  }
  return total;
 }
 void considerReadAhead(){
  if(!translate||blocked||!activelyReading()||!currentComplete())return;
  int maxScroll=Math.max(0,readerBody.getHeight()-readerScroll.getHeight());
  float progress=maxScroll>dp(48)?Math.min(1f,readerScroll.getScrollY()/(float)maxScroll):-1f;
  readAhead.consider(true,true,progress,automatic,remaining,prefs.getInt("seconds",30),displayedCharacters(),prefs.getInt("readingRate",400));
 }

 @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);prefs=getSharedPreferences("settings",MODE_PRIVATE);apiKeys=new ApiKeyStore(this);try{apiKey=apiKeys.load();}catch(Exception e){toast("已保存的 API Key 无法读取，请到「我的」重新配置");}colors();getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);if(Build.VERSION.SDK_INT>=30)getWindow().setDecorFitsSystemWindows(false);root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);root.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets s=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(s.left,s.top,s.right,s.bottom);}return insets;});setContentView(root);try{if(!prefs.getBoolean("initialized",false)){store.saveNew(Book.demo());prefs.edit().putBoolean("initialized",true).apply();}}catch(Exception e){toast("示例无法保存，请检查存储空间");}showShelf();ui.postDelayed(timer,1000);if(state!=null){String id=state.getString("book");if(id!=null)openBook(id,state.getInt("position",0));}}
 @Override protected void onResume(){super.onResume();foreground=true;lastTick=SystemClock.elapsedRealtime();pump();}
 @Override protected void onPause(){foreground=false;lastTick=0;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onPause();}
 @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);if(book!=null){b.putString("book",book.id);b.putInt("position",book.position);}}
 @Override protected void onDestroy(){foreground=false;translate=false;ui.removeCallbacks(timer);io.shutdown();network.shutdown();super.onDestroy();}
 @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(book!=null){paginate();showReader();}else showTab();}
 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}float sp(float n){return n*getResources().getDisplayMetrics().scaledDensity;}
 void colors(){String t=prefs.getString("theme","paper");if(t.equals("night")){bg=Color.rgb(30,40,37);text=Color.rgb(216,226,212);muted=Color.rgb(153,170,156);accent=Color.rgb(171,200,176);surface=Color.rgb(43,56,48);}else{bg=Color.parseColor(t.equals("sepia")?"#E9DDC7":t.equals("sage")?"#DEE8DC":"#F6F4ED");text=Color.parseColor("#293C32");muted=Color.parseColor("#7C897B");accent=Color.parseColor("#345C4B");surface=Color.parseColor(t.equals("sepia")?"#F2E8D7":t.equals("sage")?"#EDF3E8":"#FFFEF9");}if(root!=null)root.setBackgroundColor(bg);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);View decor=getWindow().getDecorView();decor.setSystemUiVisibility(t.equals("night")?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
 GradientDrawable rounded(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(1);return v;}LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 TextView label(String s,float size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 TextView button(String s,Runnable action){TextView v=label(s,13,accent);v.setGravity(Gravity.CENTER);v.setPadding(dp(12),dp(10),dp(12),dp(10));v.setMinHeight(dp(46));v.setBackground(rounded(surface,10));v.setOnClickListener(w->action.run());return v;}
 void gap(LinearLayout v,int height){v.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
 void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
 void title(LinearLayout v,String title,String subtitle){TextView h=label(title,27,text);h.setTypeface(Typeface.create("serif",Typeface.NORMAL));v.addView(h);if(!subtitle.isEmpty()){gap(v,8);v.addView(label(subtitle,12,muted));}gap(v,22);}
 LinearLayout pageContainer(){root.removeAllViews();colors();LinearLayout frame=column();frame.setPadding(dp(22),dp(18),dp(22),dp(12));root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));return frame;}
 void nav(){LinearLayout n=row();n.setPadding(dp(12),dp(4),dp(12),dp(6));String[] names={"书架","阅读历史","我的"};for(int i=0;i<3;i++){final int k=i;TextView b=button(names[i],()->{tab=k;showTab();});b.setTextColor(tab==i?accent:muted);n.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(n);}
 void showTab(){if(tab==0)showShelf();else if(tab==1)showHistory();else showSettings();}
 void showShelf(){book=null;translate=false;automatic=false;error="";getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);LinearLayout frame=pageContainer();LinearLayout header=row();TextView brand=label("页间   LEAF",22,accent);brand.setTypeface(null,Typeface.BOLD);header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));header.addView(button("＋ 导入",()->chooseFile(100)));frame.addView(header);gap(frame,25);title(frame,"把故事，带在身边。","随读随译 · 快读完时，准备下一段");EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(14);search.setTextColor(text);search.setHintTextColor(muted);search.setHint("寻找书名或作者");frame.addView(search);gap(frame,10);ScrollView scroll=new ScrollView(this);LinearLayout list=column();scroll.addView(list);frame.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));Runnable populate=()->{list.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);for(Store.Entry e:store.list()){if(!(e.title+e.author).toLowerCase(Locale.ROOT).contains(q))continue;LinearLayout card=row();card.setPadding(dp(14),dp(18),dp(12),dp(18));card.setBackground(rounded(surface,13));TextView cover=label(e.format,12,bg);cover.setGravity(Gravity.CENTER);cover.setBackground(rounded(accent,7));card.addView(cover,new LinearLayout.LayoutParams(dp(52),dp(76)));LinearLayout words=column();words.setPadding(dp(16),0,dp(8),0);TextView name=label(e.title,17,text);name.setMaxLines(2);words.addView(name);gap(words,7);words.addView(label(e.author,11,muted));gap(words,5);words.addView(label(e.lastRead==0?"未开始":"已读 "+Math.round(e.position*100f/Math.max(1,e.total-1))+"%",11,accent));card.addView(words,new LinearLayout.LayoutParams(0,-2,1));card.addView(label("›",24,muted));card.setOnClickListener(v->openBook(e.id,-1));card.setOnLongClickListener(v->{manage(e);return true;});list.addView(card);gap(list,13);}if(list.getChildCount()==0)list.addView(label("还没有找到书籍，点击右上角导入。",14,muted));};populate.run();search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){populate.run();}public void afterTextChanged(Editable e){}});nav();}
 void manage(Store.Entry e){dialog(new AlertDialog.Builder(this).setTitle(e.title).setItems(new String[]{"从头阅读","删除书籍及译文"},(d,n)->{if(n==0)openBook(e.id,0);else dialog(new AlertDialog.Builder(this).setTitle("删除这本书？").setMessage("本机书籍、译文和历史将被移除。").setNegativeButton("取消",null).setPositiveButton("删除",(dd,x)->{store.remove(e.id);showShelf();}).create());}).create());}
 void showHistory(){LinearLayout f=pageContainer();title(f,"阅读足迹","每次回来，都能接着读。 ");ScrollView s=new ScrollView(this);LinearLayout list=column();for(Store.Visit h:store.history()){LinearLayout c=column();c.setPadding(dp(16),dp(16),dp(16),dp(16));c.setBackground(rounded(surface,12));c.addView(label(new java.text.SimpleDateFormat("MM月dd日 HH:mm",Locale.CHINA).format(new Date(h.time)),11,muted));gap(c,8);c.addView(label(h.title,18,text));gap(c,7);c.addView(label("继续上次的阅读  ›",12,accent));c.setOnClickListener(v->openBook(h.bookId,h.position));list.addView(c);gap(list,13);}if(list.getChildCount()==0)list.addView(label("打开一本书，留下第一段阅读足迹。",14,muted));s.addView(list);f.addView(s,new LinearLayout.LayoutParams(-1,0,1));f.addView(button("清空历史",()->dialog(new AlertDialog.Builder(this).setTitle("清空阅读历史？").setMessage("书籍和阅读位置会保留。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{store.clearHistory();showHistory();}).create())));nav();}
 void showSettings(){
  LinearLayout f=pageContainer();title(f,"我的","翻译配置与阅读习惯，都放在这里。");
  ScrollView scroll=new ScrollView(this);LinearLayout items=column();
  LinearLayout identity=row();identity.setPadding(dp(16),dp(18),dp(16),dp(18));identity.setBackground(rounded(surface,14));
  TextView avatar=label("页",25,bg);avatar.setGravity(Gravity.CENTER);avatar.setBackground(rounded(accent,28));identity.addView(avatar,new LinearLayout.LayoutParams(dp(56),dp(56)));
  LinearLayout words=column();words.setPadding(dp(16),0,0,0);words.addView(label("我的阅读空间",19,text));gap(words,6);words.addView(label(store.list().size()+" 本书  ·  本机保存",12,muted));identity.addView(words);items.addView(identity);gap(items,18);
  String keyState=apiKey.isEmpty()?"未配置 · 点击填写并保存 API Key":"Key 已加密保存 · "+prefs.getString("model","deepseek-flash");
  addSetting(items,"DeepSeek API 配置",keyState,()->translationSettings());
  addSetting(items,"随读随译",(prefs.getBoolean("free",false)?"免费 Google":"DeepSeek")+" · "+(prefs.getBoolean("autoTranslate",false)?"打开书籍自动翻译":"阅读时手动开启"),()->translationSettings());
  addSetting(items,"预译时机","读到后段时预译下一页 · 调整阅读速度",()->readAheadSettings());
  addSetting(items,"字体与阅读背景","字号、行距、留白、自动翻页",()->appearance());
  addSetting(items,"导出书架备份","包含书籍、译文和历史；不包含 API Key",()->chooseExport());
  addSetting(items,"恢复书架备份","导入本应用生成的 JSON 备份",()->chooseFile(102));
  addSetting(items,"使用说明","阅读不需要联网，翻译需要联网",()->about());
  scroll.addView(items);f.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));gap(f,12);f.addView(label("页间 1.2 · 原生 Android",12,muted));nav();
 }
 void readAheadSettings(){
  LinearLayout f=column();f.setPadding(dp(22),dp(10),dp(22),dp(20));
  f.addView(label("先译当前内容，快读完时再准备下一部分。",15,accent));gap(f,14);
  f.addView(label("长页：滚动到后段（约 70%）时预译下一页。\n一屏能看完的页面：按停留时间和阅读速度估算。\n自动翻页：剩余约 30% 时间时开始预译。\n一次最多提前准备下一页，停在同一页不会继续往后译。",13,text));
  final int[] rate={prefs.getInt("readingRate",400)};slider(f,"阅读速度",200,800,rate," 字/分钟");
  gap(f,8);f.addView(label("预译偏晚可调快；偏早可调慢。菜单、设置和后台停留不计入阅读时间。",12,muted));
  dialog(new AlertDialog.Builder(this).setTitle("预译时机").setView(f).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{
   prefs.edit().putInt("readingRate",rate[0]).apply();resetReadProgress();if(book==null)showSettings();else updateStatus();
  }).create());
 }

 void addSetting(LinearLayout list,String name,String subtitle,Runnable click){LinearLayout card=column();card.setPadding(dp(16),dp(17),dp(16),dp(17));card.setBackground(rounded(surface,12));card.addView(label(name+"   ›",16,text));gap(card,7);card.addView(label(subtitle,11,muted));card.setOnClickListener(v->click.run());list.addView(card);gap(list,13);}
 void dialog(AlertDialog d){modal=true;d.setOnDismissListener(x->{modal=false;lastTick=SystemClock.elapsedRealtime();pump();});d.show();}
 void about(){dialog(new AlertDialog.Builder(this).setTitle("页间 · 原生阅读器").setMessage("导入 EPUB、TXT、DOCX 后，点击屏幕右侧或向左滑动翻到下一页；点击左侧或向右滑动返回。点击中央显示或隐藏菜单。Android 15 及以上支持文字型 PDF；扫描版请先 OCR。此版本为文字阅读版，不保留原图与复杂版式。\n\n长按书架上的书可删除或从头阅读。点「样式」调整字体、背景和自动翻页间隔。长页面可上下滚动。\n\n开启翻译后，先准备当前页；长页滚动到约 70%，或一屏页面停留接近估计阅读时间的 70% 时，才准备紧接着的一页。自动模式按翻页倒计时触发。应用不能知道眼睛读到哪里，可在「我的 → 预译时机」调整阅读速度。已有译文按原文片段缓存，改字号不会重复请求已译片段。正在发送的请求可能已产生费用，会完成并保存；未发送的旧任务会在跳页后丢弃。\n\n免费通道可能限流。DeepSeek 由手机直接请求官方 API，使用你的 API 余额。在「我的 → DeepSeek API 配置」填写 Key，可在本机加密保存。重开应用会自动读取，不包含在书架备份中。\n\n所有书籍和译文保存在本机，卸载会删除。建议定期导出备份。备份与之前网页版本格式不同。\n\n无需网页或网站账号。APK 已做构建验证，尚未做实机和真实付费 Key 测试。").setPositiveButton("知道了",null).create());}
 void chooseFile(int code){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");// Some providers label EPUB as ZIP or use a vendor MIME; sniff the selected bytes instead.
 try{startActivityForResult(intent,code);}catch(Exception e){toast("未找到文件选择器");}}
 void chooseExport(){Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,"页间备份-"+new java.text.SimpleDateFormat("yyyyMMdd",Locale.ROOT).format(new Date())+".json");startActivityForResult(intent,101);}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();if(req==102){dialog(new AlertDialog.Builder(this).setTitle("恢复备份？").setMessage("合并书架，并更新同一本书的阅读位置与译文。").setNegativeButton("取消",null).setPositiveButton("恢复",(d,w)->runFileTask(()->store.restore(getContentResolver().openInputStream(uri)),"备份已恢复")).create());return;}if(req==101){runFileTask(()->store.exportTo(getContentResolver().openOutputStream(uri)),"备份已导出");return;}if(req==100){String name=null;try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}catch(Exception ignored){}final String filename=name;runFileTask(()->{Book b=Importer.load(this,uri,filename);if(store.exists(b.id))throw new IOException("这本书已经在书架上，已保留原进度");store.saveNew(b);},"书籍已加入书架");}}
 interface Job{void run()throws Exception;}
 void runFileTask(Job task,String success){ProgressDialog wait=new ProgressDialog(this);wait.setMessage("正在处理文件，请稍候…");wait.setCancelable(false);modal=true;wait.show();io.execute(()->{String message=success;try{task.run();}catch(Exception e){message=errorMessage(e);}final String msg=message;ui.post(()->{if(isFinishing()||isDestroyed())return;wait.dismiss();modal=false;toast(msg);showTab();});});}
 String errorMessage(Exception e){String s=e.getMessage();if(e instanceof org.json.JSONException)return "服务返回格式异常或文档内容无效";if(e instanceof java.net.SocketTimeoutException)return "网络连接超时，请稍后重试";if(e instanceof java.net.UnknownHostException)return "无法连接翻译服务，请检查网络";if(s==null||s.length()>200)return "处理失败："+e.getClass().getSimpleName();return apiKey.isEmpty()?s:s.replace(apiKey,"[已隐藏]");}
 void openBook(String id,int pos){toast("正在打开书籍…");io.execute(()->{try{Book b=store.load(id);if(pos>=0)b.position=Math.min(pos,b.units.size()-1);ui.post(()->{if(isFinishing()||isDestroyed())return;book=b;translate=prefs.getBoolean("autoTranslate",false)&&(prefs.getBoolean("free",false)||!apiKey.isEmpty());blocked=false;automatic=false;menuVisible=false;selectingText=false;error="";reloadCache();paginate();store.position(book,book.position);showReader();});}catch(Exception e){ui.post(()->toast(errorMessage(e)));}});}
 int page(){return book==null?0:Core.pageAt(pages,book.position);}
 Typeface face(){return Typeface.create(prefs.getString("font","serif"),Typeface.NORMAL);}
 void paginate(){if(book==null)return;final float width=(root.getWidth()>0?root.getWidth()-root.getPaddingLeft()-root.getPaddingRight():getResources().getDisplayMetrics().widthPixels)-dp(prefs.getInt("margin",24)*2);final float height=(root.getHeight()>0?root.getHeight()-root.getPaddingTop()-root.getPaddingBottom():getResources().getDisplayMetrics().heightPixels-dp(60))-dp(76);final float font=sp(prefs.getInt("size",20));final float line=prefs.getFloat("line",1.8f);final android.text.TextPaint paint=new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setTypeface(face());pages=Core.paginate(book.units,u->{paint.setTextSize(u.heading?font*1.22f:font);StaticLayout layout=StaticLayout.Builder.obtain(u.text,0,u.text.length(),paint,(int)Math.max(dp(160),width)).setLineSpacing(0,line).setIncludePad(false).build();return layout.getHeight()+dp(u.heading?26:18);},Math.max(dp(180),height));}
 void showReader(){
  if(book==null)return;
  resetReadProgress();readerGesture.cancel();root.removeAllViews();colors();
  readerFrame=new FrameLayout(this);
  root.addView(readerFrame,new LinearLayout.LayoutParams(-1,0,1));
  readerScroll=new ScrollView(this);readerScroll.setFillViewport(false);
  readerScroll.setVerticalScrollBarEnabled(false);
  readerScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(!layingOut){considerReadAhead();pump();updateStatus();}});
  readerBody=column();int margin=prefs.getInt("margin",24);
  readerBody.setPadding(dp(margin),dp(22),dp(margin),dp(42));
  readerScroll.addView(readerBody);
  readerFrame.addView(readerScroll,new FrameLayout.LayoutParams(-1,-1));
  renderText(false);

  quietProgress=label("",10,muted);quietProgress.setGravity(Gravity.CENTER);
  quietProgress.setBackgroundColor(bg);quietProgress.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  readerFrame.addView(quietProgress,new FrameLayout.LayoutParams(-1,dp(26),Gravity.BOTTOM));

  // Overlay controls: opening the menu never changes pagination or translation requests.
  readerTop=column();readerTop.setBackgroundColor(bg);readerTop.setElevation(dp(6));readerTop.setClickable(true);
  LinearLayout top=row();top.setPadding(dp(8),dp(8),dp(8),dp(5));
  top.addView(button("‹ 书架",()->closeReader()));
  LinearLayout title=column();TextView name=label(book.title,14,text);
  name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);title.addView(name);
  TextView chapter=label(book.units.get(book.position).chapter,10,muted);chapter.setGravity(Gravity.CENTER);chapter.setMaxLines(1);title.addView(chapter);
  top.addView(title,new LinearLayout.LayoutParams(0,-2,1));top.addView(button("样式",()->appearance()));readerTop.addView(top);
  status=label("",11,muted);status.setPadding(dp(22),dp(7),dp(22),dp(10));
  status.setOnClickListener(v->{if(blocked){blocked=false;error="";setMenuVisible(false);pump();}else translationSettings();});readerTop.addView(status);
  readerFrame.addView(readerTop,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

  readerBottom=column();readerBottom.setBackgroundColor(bg);readerBottom.setElevation(dp(6));readerBottom.setClickable(true);
  readerBottom.setPadding(dp(8),dp(4),dp(8),dp(7));
  LinearLayout progress=row();pageLabel=label("",11,muted);pageLabel.setPadding(dp(10),0,dp(9),0);progress.addView(pageLabel);
  SeekBar seek=new SeekBar(this);seek.setContentDescription("阅读进度");seek.setMax(Math.max(0,pages.size()-1));seek.setProgress(page());
  progress.addView(seek,new LinearLayout.LayoutParams(0,dp(36),1));
  seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){flip(s.getProgress());}});
  readerBottom.addView(progress);
  LinearLayout actions=row();actions.addView(button("目录",()->chapters()),new LinearLayout.LayoutParams(0,dp(48),1));
  translateButton=button("翻译",()->toggleTranslation());translateButton.setOnLongClickListener(v->{translationSettings();return true;});
  actions.addView(translateButton,new LinearLayout.LayoutParams(0,dp(48),1));
  autoButton=button("自动",()->{automatic=!automatic;remaining=prefs.getInt("seconds",30);if(automatic)setMenuVisible(false);updateStatus();});
  actions.addView(autoButton,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView prev=button("‹ 上页",()->flip(page()-1));prev.setEnabled(page()>0);actions.addView(prev,new LinearLayout.LayoutParams(0,dp(48),1));
  TextView next=button("下页 ›",()->flip(page()+1));next.setEnabled(page()<pages.size()-1);actions.addView(next,new LinearLayout.LayoutParams(0,dp(48),1));
  readerBottom.addView(actions);readerFrame.addView(readerBottom,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
  setMenuVisible(menuVisible);remaining=prefs.getInt("seconds",30);updateStatus();pump();
  if(!prefs.getBoolean("gestureHintV11",false)){
   toast("点击右侧或左滑翻页，点击中央显示菜单。长按文字可选择复制。");
   prefs.edit().putBoolean("gestureHintV11",true).apply();
  }
 }
 void setMenuVisible(boolean visible){
  menuVisible=visible;
  if(readerTop!=null)readerTop.setVisibility(visible?View.VISIBLE:View.GONE);
  if(readerBottom!=null)readerBottom.setVisibility(visible?View.VISIBLE:View.GONE);
  if(quietProgress!=null)quietProgress.setVisibility(visible?View.GONE:View.VISIBLE);
  if(book!=null&&pageLabel!=null&&translateButton!=null&&autoButton!=null)updateStatus();
 }

 void renderText(boolean preserve){if(book==null||readerBody==null||pages.isEmpty())return;layingOut=true;int y=preserve?readerScroll.getScrollY():0;readerBody.removeAllViews();Core.Page p=pages.get(page());String mode=prefs.getString("view","translation");for(int i=p.start;i<p.end;i++){Core.Unit u=book.units.get(i);String t=cached.get(i);if(mode.equals("bilingual")&&t!=null){TextView original=readerText(u.text,u.heading);original.setTextSize(prefs.getInt("size",20)*.82f);original.setTextColor(muted);readerBody.addView(original);gap(readerBody,8);}String display=mode.equals("original")||t==null?u.text:t;TextView paragraph=readerText(display,u.heading);readerBody.addView(paragraph);if(translate&&t==null){TextView note=label(blocked?"暂显示原文":"待译 · 原文",9,muted);readerBody.addView(note);}gap(readerBody,u.heading?24:18);}final ScrollView target=readerScroll;final LinearLayout body=readerBody;final long generation=readingGeneration;
  body.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){
   if(body.getViewTreeObserver().isAlive())body.getViewTreeObserver().removeOnGlobalLayoutListener(this);
   if(readerScroll!=target||generation!=readingGeneration||book==null)return;
   target.scrollTo(0,y);layingOut=false;considerReadAhead();pump();
  }});
 }
 TextView readerText(String s,boolean heading){TextView v=label(s,prefs.getInt("size",20)*(heading?1.22f:1),text);v.setTypeface(face(),heading?Typeface.BOLD:Typeface.NORMAL);v.setLineSpacing(0,prefs.getFloat("line",1.8f));v.setTextIsSelectable(true);return v;}
 void flip(int next){if(book==null||next<0||next>=pages.size()||next==page())return;book.position=pages.get(next).start;blocked=false;error="";store.position(book,book.position);showReader();}
 void closeReader(){translate=false;automatic=false;book=null;readerBody=null;readerScroll=null;readerTop=null;readerBottom=null;quietProgress=null;readerGesture.cancel();menuVisible=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);showShelf();}
 @Override public void onBackPressed(){if(book!=null&&menuVisible)setMenuVisible(false);else if(book!=null)closeReader();else super.onBackPressed();}
 boolean inside(View v,float x,float y){
  if(v==null||v.getVisibility()!=View.VISIBLE)return false;
  int[] at=new int[2];v.getLocationOnScreen(at);
  return x>=at[0]&&x<at[0]+v.getWidth()&&y>=at[1]&&y<at[1]+v.getHeight();
 }
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  if(book!=null&&!modal&&readerScroll!=null&&!selectingText){
   int[] at=new int[2];readerScroll.getLocationOnScreen(at);
   float x=e.getRawX()-at[0],y=e.getRawY()-at[1];
   switch(e.getActionMasked()){
    case MotionEvent.ACTION_DOWN:
     if(inside(readerScroll,e.getRawX(),e.getRawY())&&!inside(readerTop,e.getRawX(),e.getRawY())&&!inside(readerBottom,e.getRawX(),e.getRawY()))
      readerGesture.down(x,y,readerScroll.getWidth(),ViewConfiguration.get(this).getScaledTouchSlop(),Math.max(dp(48),readerScroll.getWidth()*.14f),e.getEventTime(),ViewConfiguration.getLongPressTimeout());
     else readerGesture.cancel();
     break;
    case MotionEvent.ACTION_MOVE:readerGesture.move(x,y);break;
    case MotionEvent.ACTION_POINTER_DOWN:case MotionEvent.ACTION_CANCEL:readerGesture.cancel();break;
    case MotionEvent.ACTION_UP:
     int action=readerGesture.up(x,y,e.getEventTime());
     if(action!=ReaderGesture.NONE){
      MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);super.dispatchTouchEvent(cancel);cancel.recycle();
      if(action==ReaderGesture.MENU)setMenuVisible(!menuVisible);
      else {setMenuVisible(false);int next=page()+(action==ReaderGesture.NEXT?1:-1);if(next<0||next>=pages.size())toast(next<0?"已经是第一页":"已经是最后一页");else flip(next);}
      return true;
     }
     break;
   }
  }else readerGesture.cancel();
  return super.dispatchTouchEvent(e);
 }
 @Override public void onActionModeStarted(ActionMode mode){super.onActionModeStarted(mode);selectingText=true;readerGesture.cancel();}
 @Override public void onActionModeFinished(ActionMode mode){super.onActionModeFinished(mode);selectingText=false;}

 void chapters(){if(book==null)return;List<String> titles=new ArrayList<>();List<Integer> positions=new ArrayList<>();String previous="";for(int i=0;i<book.units.size();i++){String c=book.units.get(i).chapter;if(!c.equals(previous)){titles.add(c);positions.add(i);previous=c;}}dialog(new AlertDialog.Builder(this).setTitle("章节目录").setItems(titles.toArray(new String[0]),(d,n)->flip(Core.pageAt(pages,positions.get(n)))).setNegativeButton("关闭",null).create());}
 Translator.Config config(){Translator.Config c=new Translator.Config();c.free=prefs.getBoolean("free",false);c.key=apiKey;c.model=prefs.getString("model","deepseek-flash");c.source=prefs.getString("source","auto");c.target=prefs.getString("target","zh-CN");c.glossary=prefs.getString("glossary","");return c;}
 void reloadCache(){if(book==null)return;profile=config().profile();cached=store.cache(book.id,profile);}
 void toggleTranslation(){
  if(translate){translate=false;blocked=false;error="";prefs.edit().putBoolean("autoTranslate",false).apply();resetReadProgress();renderText(true);updateStatus();return;}
  if(!config().free&&apiKey.trim().isEmpty()){translationSettings();return;}
  translate=true;blocked=false;error="";prefs.edit().putBoolean("autoTranslate",true).apply();resetReadProgress();setMenuVisible(false);renderText(true);pump();updateStatus();
 }
 boolean pageComplete(int index){
  if(book==null||index<0||index>=pages.size())return true;
  for(int i:ReadAhead.pageUnits(pages,index))if(!cached.containsKey(i))return false;return true;
 }
 boolean currentComplete(){return pageComplete(page());}
 String translationPhase(){
  if(blocked)return "翻译暂停 · 点击中央重试";
  if(!translate)return "原文 / 缓存阅读";
  if(!currentComplete())return "正在准备当前内容";
  if(page()+1<pages.size()&&pageComplete(page()+1))return "下一部分已备好";
  return readAhead.allowed()&&page()+1<pages.size()?"正在预译下一部分":"随读随译";
 }
 void updateStatus(){
  if(book==null||status==null||pages.isEmpty())return;
  int p=page();pageLabel.setText((p+1)+" / "+pages.size()+" 页");
  if(quietProgress!=null)quietProgress.setText((p+1)+" / "+pages.size()+"  ·  "+translationPhase());
  translateButton.setText(translate?"翻译开":"翻译");
  autoButton.setText(automatic?menuVisible?"暂停中":translate&&!currentComplete()?"等译文":remaining+"秒":"自动");
  if(automatic&&foreground)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if(blocked){status.setText(error+" · 点击重试");return;}
  if(!translate){status.setText("点击「翻译」开启 · API 可在「我的」配置");return;}
  List<Integer> current=ReadAhead.pageUnits(pages,p);int count=0;for(int i:current)if(cached.containsKey(i))count++;
  if(count<current.size())status.setText("优先翻译当前内容  ·  "+count+"/"+current.size()+" 段");
  else if(p+1>=pages.size())status.setText("当前内容已译好 · 已到最后一页");
  else if(pageComplete(p+1))status.setText("当前内容已译好 · 下一部分已备好");
  else if(readAhead.allowed())status.setText("当前内容已译好 · 正在准备紧接着的一页");
  else status.setText("当前内容已译好 · 接近读完时再预译下一页");
 }
 void pump(){
  if(book==null||!translate||!foreground||modal||menuVisible||selectingText||blocked||networkBusy)return;
  final Book taskBook=book;final Translator.Config conf=config();final String taskProfile=conf.profile();
  if(!taskProfile.equals(profile)){reloadCache();resetReadProgress();}
  final long generation=readingGeneration;
  final List<Integer> ids=ReadAhead.nextBatch(pages,page(),cached.keySet(),readAhead.allowed(),3);
  if(ids.isEmpty()){updateStatus();return;}
  List<String> texts=new ArrayList<>();for(int id:ids)texts.add(book.units.get(id).text);
  networkBusy=true;updateStatus();
  network.execute(()->{
   String failure=null;
   try{List<String> values=Translator.translate(conf,ids,texts);store.cache(taskBook.id,taskProfile,ids,values);}
   catch(Exception e){failure=errorMessage(e);if(!conf.key.isEmpty())failure=failure.replace(conf.key,"[已隐藏]");}
   final String problem=failure;
   ui.post(()->{
    networkBusy=false;if(isDestroyed()||isFinishing())return;
    if(book!=null&&book.id.equals(taskBook.id)&&profile.equals(taskProfile)){
     // An old-page failure must not block a new reading position.
     if(problem!=null){if(generation==readingGeneration){blocked=true;error=problem;}}
     else{
      reloadCache();Core.Page p=pages.get(page());boolean affects=false;for(int id:ids)if(id>=p.start&&id<p.end)affects=true;
      if(affects){lastTick=SystemClock.elapsedRealtime();renderText(true);}
     }
     updateStatus();
    }
    pump();
   });
  });
 }

 void appearance(){LinearLayout form=column();form.setPadding(dp(22),dp(10),dp(22),dp(14));final int[] size={prefs.getInt("size",20)},margin={prefs.getInt("margin",24)},seconds={prefs.getInt("seconds",30)},spacing={Math.round(prefs.getFloat("line",1.8f)*100)};slider(form,"字号",14,32,size," sp");String[] fontIds={"serif","sans-serif","monospace"};Spinner fonts=spinner(form,"字体",new String[]{"衬线 / 宋体风格","系统无衬线","等宽字体"},index(fontIds,prefs.getString("font","serif")));String[] themeIds={"paper","sepia","sage","night"};Spinner theme=spinner(form,"阅读背景",new String[]{"纸白","暖棕","浅绿","夜读"},index(themeIds,prefs.getString("theme","paper")));slider(form,"行距（百分比）",130,240,spacing,"%");slider(form,"两侧留白",16,42,margin," dp");slider(form,"自动翻页间隔",10,180,seconds," 秒");String[] viewIds={"translation","original","bilingual"};Spinner mode=spinner(form,"显示方式",new String[]{"优先显示缓存译文","只显示原文","原文＋译文"},index(viewIds,prefs.getString("view","translation")));TextView hint=label("译文过长时可上下滚动；自动模式会先滚完再翻页。",12,muted);form.addView(hint);ScrollView scroll=new ScrollView(this);scroll.addView(form);dialog(new AlertDialog.Builder(this).setTitle("读得舒服一点").setView(scroll).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{prefs.edit().putInt("size",size[0]).putInt("margin",margin[0]).putInt("seconds",seconds[0]).putFloat("line",spacing[0]/100f).putString("font",fontIds[fonts.getSelectedItemPosition()]).putString("theme",themeIds[theme.getSelectedItemPosition()]).putString("view",viewIds[mode.getSelectedItemPosition()]).apply();colors();if(book!=null){paginate();showReader();}else showTab();}).create());}
 int index(String[] values,String value){for(int i=0;i<values.length;i++)if(values[i].equals(value))return i;return 0;}
 Spinner spinner(LinearLayout form,String label,String[] values,int selected){gap(form,14);form.addView(label(label,13,text));Spinner s=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(selected);form.addView(s,new LinearLayout.LayoutParams(-1,dp(49)));return s;}
 void slider(LinearLayout form,String name,int min,int max,int[] value,String suffix){gap(form,12);TextView title=label(name+"  "+value[0]+suffix,13,text);form.addView(title);SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(value[0]-min);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int n,boolean user){value[0]=n+min;title.setText(name+"  "+value[0]+suffix);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});form.addView(seek,new LinearLayout.LayoutParams(-1,dp(42)));}
 EditText field(LinearLayout form,String name,String value,String hint,boolean secret){gap(form,12);form.addView(label(name,13,text));EditText e=new EditText(this);e.setTextSize(14);e.setTextColor(text);e.setHintTextColor(muted);e.setText(value);e.setHint(hint);e.setSingleLine(true);if(secret){e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);e.setSaveEnabled(false);e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);}form.addView(e);return e;}
 void translationSettings(){
  LinearLayout f=column();f.setPadding(dp(22),dp(10),dp(22),dp(20));
  f.addView(label("先译当前内容，快读完时再预译下一页。",13,accent));
  Spinner provider=spinner(f,"翻译通道",new String[]{"DeepSeek API","免费 Google（可能限流）"},prefs.getBoolean("free",false)?1:0);
  EditText key=field(f,"DeepSeek API Key",apiKey,"粘贴 API Key，保存后下次自动读取",true);
  CheckBox reveal=new CheckBox(this);reveal.setText("显示 Key");reveal.setTextColor(muted);f.addView(reveal);
  reveal.setOnCheckedChangeListener((b,checked)->{key.setTransformationMethod(checked?null:android.text.method.PasswordTransformationMethod.getInstance());key.setSelection(key.length());});
  f.addView(label(apiKey.isEmpty()?"尚未保存 Key":"已在本机加密保存；修改后点击保存生效",11,muted));
  EditText model=field(f,"DeepSeek 模型",prefs.getString("model","deepseek-flash"),"deepseek-flash",false);
  String[] sourceIds={"auto","en","fr","ja","zh-CN"};
  Spinner source=spinner(f,"原文语言",new String[]{"自动识别","英语","法语","日语","中文"},index(sourceIds,prefs.getString("source","auto")));
  String[] targetIds={"zh-CN","zh-TW","en","fr","ja"};
  Spinner target=spinner(f,"目标语言",new String[]{"简体中文","繁体中文","英语","法语","日语"},index(targetIds,prefs.getString("target","zh-CN")));
  CheckBox autoStart=new CheckBox(this);autoStart.setText("打开书籍时自动开始随读随译");autoStart.setTextColor(text);autoStart.setChecked(prefs.getBoolean("autoTranslate",true));gap(f,14);f.addView(autoStart);
  EditText glossary=field(f,"术语表（仅 DeepSeek）",prefs.getString("glossary",""),"ThunderClan = 雷族",false);
  glossary.setSingleLine(false);glossary.setMinLines(2);glossary.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8000)});
  gap(f,14);f.addView(label("Key 只在本机加密保存，不放进书架备份。手机直接向所选服务发送需要翻译的片段；DeepSeek 按调用计费。",12,muted));
  gap(f,12);f.addView(button("清除本机已保存的 Key",()->{
   try{apiKeys.clear();apiKey="";key.setText("");if(!prefs.getBoolean("free",false))translate=false;resetReadProgress();toast("已清除本机 API Key");if(book!=null)updateStatus();}
   catch(Exception e){toast("清除失败，请重试");}
  }));
  ScrollView scroll=new ScrollView(this);scroll.addView(f);
  AlertDialog d=new AlertDialog.Builder(this).setTitle("翻译与 DeepSeek API 配置").setView(scroll).setNegativeButton("取消",null).setPositiveButton(book==null?"保存配置":"保存并翻译",null).create();
  d.setOnDismissListener(x->{modal=false;lastTick=SystemClock.elapsedRealtime();if(book==null&&tab==2)showSettings();else pump();});
  modal=true;d.show();if(d.getWindow()!=null)d.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
  d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
   boolean free=provider.getSelectedItemPosition()==1;String nextKey=key.getText().toString().trim();String nextModel=model.getText().toString().trim();
   if(!free&&(nextKey.isEmpty()||nextModel.isEmpty())){toast("请填写 API Key 和模型名，或选择免费通道");return;}
   if(sourceIds[source.getSelectedItemPosition()].equals(targetIds[target.getSelectedItemPosition()])){toast("原文和目标语言应不同");return;}
   try{apiKeys.save(nextKey);}catch(Exception e){toast("API Key 未能加密保存，请检查手机存储后重试");return;}
   apiKey=nextKey;
   prefs.edit().putBoolean("free",free).putString("model",nextModel.isEmpty()?"deepseek-flash":nextModel)
    .putString("source",sourceIds[source.getSelectedItemPosition()]).putString("target",targetIds[target.getSelectedItemPosition()])
    .putString("glossary",glossary.getText().toString().trim()).putBoolean("autoTranslate",autoStart.isChecked()).apply();
   blocked=false;error="";resetReadProgress();
   if(book!=null){reloadCache();translate=true;setMenuVisible(false);renderText(true);updateStatus();}
   d.dismiss();toast("翻译配置已保存");
  });
 }

}
