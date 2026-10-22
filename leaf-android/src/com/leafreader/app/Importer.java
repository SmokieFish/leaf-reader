package com.leafreader.app;
import android.content.*;import android.net.Uri;import android.os.*;import android.graphics.pdf.*;import android.graphics.pdf.content.*;import android.util.Xml;
import java.io.*;import java.util.*;import java.util.zip.*;import java.net.URI;import java.security.*;import java.nio.charset.*;import org.xmlpull.v1.*;
public final class Importer {
 public static byte[] read(InputStream input,int limit)throws IOException{if(input==null)throw new IOException("文件无法打开");try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[16384];int n,total=0;while((n=in.read(buf))!=-1){total+=n;if(total>limit)throw new IOException("文件过大，请拆分后导入");out.write(buf,0,n);}return out.toByteArray();}}
 public static String sha(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
 static XmlPullParser parser(String s)throws Exception{
  XmlPullParser p=Xml.newPullParser();
  // Android enables DOCDECL by default. Manual entities are illegal until it is disabled.
  // Keep external DTDs disabled: importing a book must never fetch external resources.
  p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);
  p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
  p.setInput(new StringReader(s));
  String[][] entities={{"nbsp"," "},{"mdash","—"},{"ndash","–"},{"hellip","…"},{"lsquo","‘"},{"rsquo","’"},{"ldquo","“"},{"rdquo","”"},{"copy","©"},{"reg","®"},{"trade","™"},{"bull","•"},{"shy",""}};
  for(String[] pair:entities)p.defineEntityReplacementText(pair[0],pair[1]);
  return p;
 }
 static String str(Map<String,byte[]> z,String name)throws IOException{
  byte[] b=z.get(name);if(b==null)throw new IOException("文档缺少内容："+name);
  Charset encoding=StandardCharsets.UTF_8;
  if(b.length>=2&&((b[0]==(byte)0xff&&b[1]==(byte)0xfe)||(b[0]==(byte)0xfe&&b[1]==(byte)0xff)))encoding=StandardCharsets.UTF_16;
  else if(b.length>=4&&b[0]==0&&b[1]=='<')encoding=StandardCharsets.UTF_16BE;
  else if(b.length>=4&&b[0]=='<'&&b[1]==0)encoding=StandardCharsets.UTF_16LE;
  else {
   java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?i)<\\?xml[^>]*encoding\\s*=\\s*['\"]([^'\"]+)['\"]").matcher(new String(b,0,Math.min(b.length,300),StandardCharsets.ISO_8859_1));
   if(m.find())try{encoding=Charset.forName(m.group(1));}catch(Exception e){throw new IOException("文档使用了不支持的文字编码："+m.group(1));}
  }
  String value=new String(b,encoding);return value.startsWith("\ufeff")?value.substring(1):value;
 }
 static String attr(XmlPullParser p,String name){for(int i=0;i<p.getAttributeCount();i++)if(name.equals(p.getAttributeName(i)))return p.getAttributeValue(i);return "";}
 static String resolve(String base,String href)throws Exception{
  // Encode the ZIP base path once; href may already contain %20 and Unicode.
  URI root=new URI("https","book.invalid","/"+base,null);
  URI result=root.resolve(href.replace(" ","%20")).normalize();
  if(!"book.invalid".equals(result.getHost()))throw new IOException("EPUB 正文不能引用外部地址");
  String path=result.getPath();if(path==null||!path.startsWith("/")||path.contains("/../"))throw new IOException("EPUB 章节路径无效");
  return path.substring(1);
 }
 public static Book load(Context c,Uri uri,String name)throws Exception{
  byte[] bytes=read(c.getContentResolver().openInputStream(uri),35*1024*1024);
  String mime="";try{mime=c.getContentResolver().getType(uri);}catch(Exception ignored){}
  return loadBytes(c,bytes,name,mime);
 }
 // Kept separate from the Android picker so the same import path can be regression-tested.
 public static Book loadBytes(Context c,byte[] bytes,String name,String mime)throws Exception{
  if(bytes.length>35*1024*1024)throw new IOException("文件超过 35 MB，请拆分后导入");
  if(name==null||name.trim().isEmpty())name="未命名书籍";
  Book b=new Book();b.id=sha(bytes);b.title=name.replaceFirst("\\.[^.]+$","");
  String ext=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";
  Map<String,byte[]> zip=null;
  if(bytes.length>=4&&bytes[0]=='P'&&bytes[1]=='K'){
   zip=unzip(bytes);
   if(zip.containsKey("META-INF/container.xml"))ext="epub";
   else if(zip.containsKey("word/document.xml"))ext="docx";
   else throw new IOException("压缩包不是有效的 EPUB 或 DOCX；请先解压外层压缩包");
  }else if(bytes.length>=5&&new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-"))ext="pdf";
  else if("text/plain".equals(mime)&&!ext.equals("epub")&&!ext.equals("docx"))ext="txt";
  if((ext.equals("epub")||ext.equals("docx"))&&zip==null)throw new IOException("电子书文件不完整或格式不正确，请重新下载后导入");
  b.format=ext.toUpperCase(Locale.ROOT);
  if(ext.equals("txt")){String text;try{if(bytes.length>1&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)text=new String(bytes,StandardCharsets.UTF_16LE);else if(bytes.length>1&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)text=new String(bytes,StandardCharsets.UTF_16BE);else text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(Exception ex){text=new String(bytes,Charset.forName("GB18030"));}String chapter="正文";for(String line:text.split("\\r?\\n")){line=line.replace("\ufeff","").trim();if(line.isEmpty())continue;boolean heading=line.length()<100&&line.matches("(?i)^(第.{1,15}[章卷节].*|chapter\\s+.*|prologue.*|epilogue.*|序章.*|尾声.*)$");if(heading)chapter=line;b.add(line,chapter,heading);}}
  else if(ext.equals("epub")||ext.equals("docx")){
   if(ext.equals("docx")){XmlPullParser p=parser(str(zip,"word/document.xml"));StringBuilder paragraph=new StringBuilder();boolean text=false,heading=false;String chapter="正文";while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()==XmlPullParser.START_TAG){if(p.getName().equals("p")){paragraph.setLength(0);heading=false;}if(p.getName().equals("pStyle"))heading=attr(p,"val").toLowerCase(Locale.ROOT).contains("heading");if(p.getName().equals("t"))text=true;}else if(p.getEventType()==XmlPullParser.TEXT&&text)paragraph.append(p.getText());else if(p.getEventType()==XmlPullParser.END_TAG){if(p.getName().equals("t"))text=false;if(p.getName().equals("p")&&paragraph.length()>0){if(heading)chapter=paragraph.toString();b.add(paragraph.toString(),chapter,heading);}}}}
   else{
    if(zip.containsKey("META-INF/encryption.xml")){XmlPullParser p=parser(str(zip,"META-INF/encryption.xml"));while(p.next()!=XmlPullParser.END_DOCUMENT)if(p.getEventType()==XmlPullParser.START_TAG&&p.getName().equals("EncryptionMethod")){String alg=attr(p,"Algorithm");if(!alg.equals("http://www.idpf.org/2008/embedding")&&!alg.equals("http://ns.adobe.com/pdf/enc#RC"))throw new IOException("此 EPUB 使用 DRM，不支持读取");}}
    XmlPullParser p=parser(str(zip,"META-INF/container.xml"));String opf="";while(p.next()!=XmlPullParser.END_DOCUMENT)if(p.getEventType()==XmlPullParser.START_TAG&&p.getName().equals("rootfile")){opf=attr(p,"full-path");break;}if(opf.isEmpty())throw new IOException("EPUB 索引不存在");
    p=parser(str(zip,opf));Map<String,String> items=new HashMap<>();List<String> spine=new ArrayList<>();while(p.next()!=XmlPullParser.END_DOCUMENT){if(p.getEventType()!=XmlPullParser.START_TAG)continue;String tag=p.getName();if(tag.equals("title"))b.title=p.nextText();else if(tag.equals("creator"))b.author=p.nextText();else if(tag.equals("item"))items.put(attr(p,"id"),resolve(opf,attr(p,"href")));else if(tag.equals("itemref"))spine.add(attr(p,"idref"));}
    int chapter=0;for(String id:spine){String path=items.get(id);if(path==null||!zip.containsKey(path))throw new IOException("EPUB 缺少章节："+(path==null?id:path));try{html(b,str(zip,path),"第 "+(++chapter)+" 部分");}catch(org.xmlpull.v1.XmlPullParserException e){throw new IOException("EPUB 章节格式有误："+path+"。可用 Calibre 重新导出 EPUB 后重试",e);}}
   }
  }else if(ext.equals("pdf")){if(Build.VERSION.SDK_INT<35)throw new IOException("PDF 文字导入需要 Android 15 或以上；可先转换为 EPUB/TXT");pdf(c,bytes,b);}else throw new IOException("支持 EPUB、TXT、DOCX 和文字型 PDF");
  if(b.units.isEmpty())throw new IOException("未找到文字；扫描文件需要先 OCR");int chars=0;for(Core.Unit u:b.units)chars+=u.text.length();if(chars>5000000)throw new IOException("文字超过 500 万字符，请先拆分");return b;
 }
 static Map<String,byte[]> unzip(byte[] bytes)throws IOException{
  Map<String,byte[]> zip=new HashMap<>();int total=0,count=0;
  try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(bytes))){
   ZipEntry e;byte[] buf=new byte[8192];
   while((e=in.getNextEntry())!=null){
    if(++count>10000)throw new IOException("压缩包条目过多");if(e.isDirectory())continue;
    boolean take=e.getName().matches("(?i).*\\.(xml|opf|xhtml|html|htm)$");
    ByteArrayOutputStream out=new ByteArrayOutputStream();int n;
    while((n=in.read(buf))!=-1){total+=n;if(total>120*1024*1024)throw new IOException("文档解压后超过 120 MB");if(take)out.write(buf,0,n);}
    if(take)zip.put(e.getName(),out.toByteArray());
   }
  }catch(ZipException e){throw new IOException("电子书压缩内容损坏，请重新下载后导入",e);}
  return zip;
 }
 static void html(Book b,String html,String initial)throws Exception{
  XmlPullParser p=parser(html);StringBuilder text=new StringBuilder(),fallback=new StringBuilder();String chapter=initial;int bodyDepth=-1,blockDepth=-1,skipDepth=-1;boolean heading=false;int before=b.units.size();
  while(p.next()!=XmlPullParser.END_DOCUMENT){int event=p.getEventType();String tag=p.getName();
   if(event==XmlPullParser.START_TAG){if(tag.equalsIgnoreCase("body"))bodyDepth=p.getDepth();if(bodyDepth<0)continue;if(skipDepth<0&&(tag.equals("script")||tag.equals("style")||tag.equals("svg")||tag.equals("math")))skipDepth=p.getDepth();if(skipDepth<0&&blockDepth<0&&tag.matches("(?i)p|h[1-6]|li|td|blockquote")){blockDepth=p.getDepth();text.setLength(0);heading=tag.matches("(?i)h[1-6]");}}
   else if(event==XmlPullParser.TEXT&&bodyDepth>=0&&skipDepth<0){fallback.append(p.getText()).append(' ');if(blockDepth>=0)text.append(p.getText());}
   else if(event==XmlPullParser.END_TAG){if(skipDepth==p.getDepth())skipDepth=-1;if(blockDepth==p.getDepth()){String s=text.toString().replaceAll("\\s+"," ").trim();if(heading&&!s.isEmpty())chapter=s;b.add(s,chapter,heading);blockDepth=-1;}if(bodyDepth==p.getDepth())bodyDepth=-1;}
  }
  if(before==b.units.size())b.add(fallback.toString(),chapter,false);
 }
 static void pdf(Context context,byte[] bytes,Book b)throws Exception{File f=File.createTempFile("import-",".pdf",context.getCacheDir());try{try(FileOutputStream out=new FileOutputStream(f)){out.write(bytes);}try(ParcelFileDescriptor fd=ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY);PdfRenderer renderer=new PdfRenderer(fd)){for(int i=0;i<renderer.getPageCount();i++){try(PdfRenderer.Page page=renderer.openPage(i)){List<PdfPageTextContent> blocks=page.getTextContents();if(blocks.isEmpty())throw new IOException("PDF 第 "+(i+1)+" 页没有文字，请先 OCR");for(PdfPageTextContent t:blocks)b.add(t.getText(),"第 "+(i+1)+" 页",false);}}}}finally{f.delete();}}
}
