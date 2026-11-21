# Keep the Quick Settings Tile service (referenced from AndroidManifest.xml).
-keep public class com.custom.phantomtile.PhantomTileService {
    public *;
}
-keepclassmembers class com.custom.phantomtile.PhantomTileService {
    *;
}
