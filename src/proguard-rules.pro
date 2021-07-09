-optimizationpasses 4
-allowaccessmodification
-mergeinterfacesaggressively
-dontoptimize

-keeppackagenames gnu.kawa.*, gnu.expr.*

-keep public class * {
    public protected *;
}
