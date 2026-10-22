package com.leafreader.app;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.xmlpull.v1.*;

public final class ImporterTest {
 static int count;
 static void check(boolean value,String name){if(!value)throw new AssertionError(name);System.out.println("PASS: "+name);count++;}
 static byte[] utf(String text){return text.getBytes(StandardCharsets.UTF_8);}
 static byte[] zip(Map<String,byte[]> files)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();
  try(ZipOutputStream out=new ZipOutputStream(bytes)){
   for(Map.Entry<String,byte[]> e:files.entrySet()){out.putNextEntry(new ZipEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}
  }
  return bytes.toByteArray();
 }
 static Map<String,byte[]> fixture(){
  Map<String,byte[]> z=new LinkedHashMap<>();
  z.put("mimetype",utf("application/epub+zip"));
  z.put("META-INF/container.xml",utf("<container><rootfiles><rootfile full-path='OPS/Sub folder/content.opf'/></rootfiles></container>"));
  z.put("OPS/Sub folder/content.opf",utf("<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>测试书</dc:title><dc:creator>作者</dc:creator></metadata><manifest><item id='two' href='../Text/two.xhtml'/><item id='one' href='../Text/一%20章.xhtml#begin'/></manifest><spine><itemref idref='one'/><itemref idref='two'/></spine></package>"));
  z.put("OPS/Text/一 章.xhtml",utf("<?xml version='1.0' encoding='UTF-8'?><!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.1//EN' 'https://not-fetched.invalid/xhtml.dtd'><html><head><title>Not body</title></head><body><h1>第一章</h1><p>Hello&nbsp;<em>reader</em>&mdash;welcome&copy;.</p><script>skip script</script></body></html>"));
  z.put("OPS/Text/two.xhtml",("<?xml version='1.0' encoding='UTF-16'?><html><body><h1>第二章</h1><p>最后一段。😀</p></body></html>").getBytes(StandardCharsets.UTF_16));
  return z;
 }
 public static void main(String[] args)throws Exception{
  XmlPullParser old=android.util.Xml.newPullParser();old.setInput(new StringReader("<root/>"));
  boolean reproduced=false;try{old.defineEntityReplacementText("nbsp"," ");}catch(IllegalStateException expected){reproduced=true;}
  check(reproduced,"reproduces the old Android DOCDECL/entity crash");
  XmlPullParser fixed=Importer.parser("<p>safe&nbsp;text</p>");
  while(fixed.next()!=XmlPullParser.END_DOCUMENT){}
  check(!fixed.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL),"fixed factory parses entities with external DTD processing disabled");
  byte[] bytes=zip(fixture());Book b=Importer.loadBytes(null,bytes,"fixture.epub","application/epub+zip");
  check(b.title.equals("测试书")&&b.author.equals("作者")&&b.format.equals("EPUB"),"reads EPUB metadata");
  check(b.units.size()==4&&b.units.get(0).text.equals("第一章")&&b.units.get(2).text.equals("第二章"),"uses spine order and resolves nested, Unicode, escaped-space paths");
  check(b.units.get(1).text.equals("Hello reader—welcome©."),"preserves inline text and HTML entities without scripts");
  check(b.units.get(3).text.equals("最后一段。😀"),"decodes UTF-16 chapter text and Unicode");
  check(Importer.loadBytes(null,bytes,null,"application/octet-stream").id.equals(b.id),"imports EPUB when provider supplies no display name");
  check(Importer.loadBytes(null,bytes,"download.zip","application/zip").format.equals("EPUB"),"identifies EPUB content despite ZIP filename/MIME");
  Map<String,byte[]> missing=fixture();missing.remove("OPS/Text/two.xhtml");boolean missingRejected=false;
  try{Importer.loadBytes(null,zip(missing),"bad.epub","");}catch(IOException e){missingRejected=e.getMessage().contains("缺少章节");}
  check(missingRejected,"missing chapters produce an actionable error instead of silent text loss");
  Map<String,byte[]> drm=fixture();drm.put("META-INF/encryption.xml",utf("<encryption><EncryptionMethod Algorithm='protected-text'/></encryption>"));boolean drmRejected=false;
  try{Importer.loadBytes(null,zip(drm),"locked.epub","");}catch(IOException e){drmRejected=e.getMessage().contains("DRM");}
  check(drmRejected,"protected books remain explicitly unsupported");
  Map<String,byte[]> doc=new LinkedHashMap<>();doc.put("word/document.xml",utf("<w:document xmlns:w='urn:word'><w:body><w:p><w:r><w:t>DOCX text</w:t></w:r></w:p></w:body></w:document>"));
  check(Importer.loadBytes(null,zip(doc),"file.docx","").units.get(0).text.equals("DOCX text"),"shared parser fix also imports DOCX");
  check(Importer.loadBytes(null,utf("Chapter 1\nHello text"),"book.txt","").units.size()==2,"TXT import remains compatible");
  if(args.length>0){
   byte[] real=Files.readAllBytes(Paths.get(args[0]));Book actual=Importer.loadBytes(null,real,Paths.get(args[0]).getFileName().toString(),"application/epub+zip");
   int chars=0;Set<String> chapters=new HashSet<>();for(Core.Unit u:actual.units){chars+=u.text.length();chapters.add(u.chapter);}
   check(actual.title.equals("Warriors 01 - Into the Wild")&&actual.author.equals("Erin Hunter")&&chars>200000&&chapters.size()>20,"imports supplied Into the Wild EPUB through production importer");
   System.out.println("Actual book: "+actual.units.size()+" text units, "+chars+" characters, "+chapters.size()+" sections.");
  }
  System.out.println(count+" importer checks passed.");
 }
}
