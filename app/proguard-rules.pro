# Reflection-heavy spreadsheet and Google API models used by Excel export and Drive backups.
# Keep the workbook paths the app actually uses; allow R8 to remove POI's desktop drawing,
# signing, Outlook, Word and PowerPoint branches, which are unavailable on Android.
-keep class org.apache.poi.ss.usermodel.** { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFWorkbook { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFSheet { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFRow { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFCell { *; }
# XMLBeans resolves these generated interfaces and implementations by their
# original names from schema metadata. Keep the spreadsheet model plus the
# workbook property models used while XSSFWorkbook is initialized. Do not keep
# the entire OOXML namespace: POI 3.17's reduced schema jar intentionally omits
# parts of unrelated Word/PowerPoint schemas.
-keep class org.openxmlformats.schemas.spreadsheetml.** { *; }
-keep class org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.** { *; }
-keep class org.openxmlformats.schemas.officeDocument.x2006.customProperties.** { *; }
-keep class org.openxmlformats.schemas.package.x2006.metadata.coreProperties.** { *; }
-keep class org.apache.xmlbeans.** { *; }
# XMLBeans loads these generated schema registries through Class.forName().
# They live outside org.apache.xmlbeans/org.openxmlformats, so R8 cannot infer
# the reference and otherwise removes them from minified releases.
-keep class schemaorg_apache_xmlbeans.system.** { *; }
-keep class com.fasterxml.aalto.** { *; }
-keep class com.ctc.wstx.** { *; }
-keep class org.codehaus.stax2.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.drive.** { *; }

# ML Kit discovers barcode components through manifest metadata and Firebase's
# component registry. R8 full mode (AGP 9+) can otherwise optimize the registered
# factory to null in minified builds, causing BarcodeScanning.getClient() to crash.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.firebase.components.ComponentRuntime { *; }
-dontwarn javax.xml.stream.**
-dontwarn org.apache.xmlbeans.**
-dontwarn aQute.bnd.annotation.**
-dontwarn com.ctc.wstx.shaded.msv.**
-dontwarn com.ctc.wstx.shaded.msv_core.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.naming.**
-dontwarn javax.swing.**
-dontwarn javax.xml.bind.**
-dontwarn javax.xml.crypto.**
-dontwarn org.apache.jcp.xml.dsig.**
-dontwarn org.apache.poi.hsmf.**
-dontwarn org.apache.poi.hwpf.**
-dontwarn org.apache.xml.security.**
-dontwarn org.bouncycastle.**
-dontwarn org.etsi.**
-dontwarn org.ietf.jgss.**
-dontwarn org.junit.**
-dontwarn org.openxmlformats.schemas.drawingml.**
-dontwarn org.openxmlformats.schemas.officeDocument.x2006.docPropsVTypes.**
-dontwarn org.openxmlformats.schemas.spreadsheetml.**
-dontwarn org.openxmlformats.schemas.wordprocessingml.**
-dontwarn org.osgi.framework.**
-dontwarn org.w3c.dom.events.**
