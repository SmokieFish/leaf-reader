# Third-party components

Application code uses Android platform APIs; no external UI, analytics, advertising or translation SDK is bundled in the APK.

| Component | Use | Origin / license |
|---|---|---|
| KXmlParser | JVM import regression tests only | AOSP libcore commit `9edf43dfcc35c761d97eb9156ac4254152ddbc55`; permission notice retained in source |
| XmlPullParser / XmlPullParserException | JVM test interfaces | Same AOSP revision; see `LICENSES/XMLPULL-LICENSE.txt` (XMLPULL API public domain notice) |
| StringPool | JVM parser support | Same AOSP revision; Apache 2.0 header and `LICENSES/APACHE-2.0.txt` |
| JSON-java 20240303 | JVM context tests only, from v1.3.0 | Maven `org.json:json:20240303`; Public Domain, see `LICENSES/JSON-java-LICENSE.txt` |
| Android SDK platform/build tools | Downloaded build tools, not redistributed here | Official Google repository; Android SDK terms apply |
| ECJ 3.37.0 | Downloaded Java compiler, not redistributed here | Maven `org.eclipse.jdt:ecj:3.37.0`; Eclipse distribution terms apply |

Original third-party headers remain intact. Licenses here apply to their named components; no project-wide open-source license has been selected.
