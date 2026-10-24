# Aegis Browser (com.zai.aibrowser) release rules.
# R8 is enabled for release; library consumer rules (Compose, Room, OkHttp,
# Coroutines) cover framework reflection. The app has no JS bridge and no
# runtime reflection of its own.

# Keep readable crash traces.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
