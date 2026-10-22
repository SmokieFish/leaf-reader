These AOSP sources are test-only and are NOT compiled into the APK.

Source commit: `9edf43dfcc35c761d97eb9156ac4254152ddbc55` in `platform/libcore`.
Repository: https://android.googlesource.com/platform/libcore/

- `xml/src/main/java/org/kxml2/io/KXmlParser.java`
- `xml/src/main/java/org/xmlpull/v1/XmlPullParser.java`
- `xml/src/main/java/org/xmlpull/v1/XmlPullParserException.java`
- `luni/src/main/java/libcore/internal/StringPool.java`

Original copyright/license notices are retained in each file. The JVM `android.util.Xml` adapter uses Android's namespace + DOCDECL defaults, reproducing the version 1.0 import crash against the actual parser instead of an XML mock. Production code runs Android's installed XML implementation. Android UI behavior still needs device testing.
