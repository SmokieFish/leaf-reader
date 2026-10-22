package android.util;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** JVM adapter using the actual AOSP parser and Android's factory defaults. Test-only. */
public final class Xml {
 public static XmlPullParser newPullParser(){
  try{
   XmlPullParser p=new KXmlParser();
   p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,true);
   p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
   return p;
  }catch(XmlPullParserException e){throw new AssertionError(e);}
 }
}
