param(
    [string]$SourceDirectory,
    [ValidateSet('overlay','signal','tint','assignment','wifi','clear')]
    [string[]]$Suite = @('overlay','signal','tint','assignment','wifi','clear')
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$trio = Join-Path $root 'app/src/main/java/io/github/yixing233/hyperduo'
if (!$SourceDirectory) { $SourceDirectory = $trio }
$jdk = Join-Path $root '.tools/jdk/jdk-21.0.12.1+1/bin'
$javac = Join-Path $jdk 'javac.exe'; $java = Join-Path $jdk 'java.exe'
if (!(Test-Path $javac)) { $javac = 'javac'; $java = 'java' }
$src = Join-Path $root 'work/issue-review/tests/src'
$classes = Join-Path $root 'work/issue-review/tests/classes'
New-Item -ItemType Directory -Force $classes | Out-Null
$files = [System.Collections.Generic.List[string]]::new()
function Write-Source($name, $body) {
    $path = Join-Path $src $name
    New-Item -ItemType Directory -Force (Split-Path $path -Parent) | Out-Null
    [IO.File]::WriteAllText($path, $body, [Text.UTF8Encoding]::new($false))
    $files.Add($path)
}
function Read-Method($source, $signature) {
    $body = [regex]::Match($source, '(?s)' + [regex]::Escape($signature) + ' \{.*?\n    \}').Value
    if (!$body) { throw "Production method missing: $signature" }
    return $body
}
# Execute the real overlay class and the real tint hook. Only Android services,
# Xposed dispatch and telephony are faked. refresh + its polling guard are spliced
# unchanged so a successful fallback cannot hide the next polling decision.
$state = [IO.File]::ReadAllText((Join-Path $SourceDirectory 'TrioState.java'))
$hooks = [IO.File]::ReadAllText((Join-Path $SourceDirectory 'TrioHooks.java'))
$refresh = Read-Method $state 'void refresh()'
$poll = Read-Method $state 'private static void sampleSimsIfDue()'
$foreground = Read-Method $state 'int foreground()'
$meter = Read-Method $hooks 'private static int hookMeterTint(XposedModule module, ClassLoader cl)'
$rowInk = Read-Method $hooks 'static int rowInk(View row)'
foreach ($name in @('TrioOverlay.java','TrioZones.java')) {
    Write-Source "io/github/yixing233/hyperduo/$name" ([IO.File]::ReadAllText((Join-Path $SourceDirectory $name)))
}
foreach ($name in @('Prefs.java','TrioSettings.java','TrioAppearance.java','TrioGeometry.java')) {
    Write-Source "io/github/yixing233/hyperduo/$name" ([IO.File]::ReadAllText((Join-Path $trio $name)))
}
foreach ($name in @('android/content/SharedPreferences.java','android/os/Bundle.java')) {
    Write-Source $name ([IO.File]::ReadAllText((Join-Path "$PSScriptRoot/shims" $name)))
}
Write-Source 'android/util/DisplayMetrics.java' @'
package android.util;
public class DisplayMetrics { public int widthPixels=1080,heightPixels=2400; public float density=3f; }
'@
Write-Source 'android/content/res/Resources.java' @'
package android.content.res;
public class Resources { public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();} }
'@
Write-Source 'android/content/Context.java' @'
package android.content;
public class Context {
 public static final String WINDOW_SERVICE="window";
 public final android.view.WindowManager wm=new android.view.WindowManager();
 public Object getSystemService(String key){return wm;}
}
'@
Write-Source 'android/os/SystemClock.java' @'
package android.os;
public class SystemClock { public static long now=10000; public static long elapsedRealtime(){return now;} public static long uptimeMillis(){return now;} }
'@
Write-Source 'android/os/Looper.java' 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();} }'
Write-Source 'android/os/Handler.java' @'
package android.os;
public class Handler { public Handler(Looper l){} public void postDelayed(Runnable r,long delay){} public void removeCallbacks(Runnable r){} }
'@
Write-Source 'android/graphics/Canvas.java' 'package android.graphics; public class Canvas {}'
Write-Source 'android/graphics/Rect.java' 'package android.graphics; public class Rect {}'
Write-Source 'android/graphics/PixelFormat.java' 'package android.graphics; public class PixelFormat { public static final int TRANSLUCENT=-3; }'
Write-Source 'android/view/Gravity.java' 'package android.view; public class Gravity { public static final int TOP=48,LEFT=3; }'
Write-Source 'android/view/ViewParent.java' 'package android.view; public interface ViewParent {}'
Write-Source 'android/view/WindowInsets.java' @'
package android.view;
public class WindowInsets {
 public boolean visible=true; public int top=121;
 public boolean isVisible(int type){return visible;}
 public Insets getInsets(int type){return new Insets(top);}
 public static class Type { public static int statusBars(){return 1;} }
 public static class Insets { public int top; Insets(int t){top=t;} }
}
'@
Write-Source 'android/view/View.java' @'
package android.view;
import android.content.Context;
public class View implements ViewParent {
 public static final int VISIBLE=0;
 public Context context; public View parent; public float alpha=1;
 public boolean shown=true,attached=true,unclipped=true;
 public int width=60,height=90,x=900,y=0,windowVisibility=0,invalidations;
 public WindowInsets insets=new WindowInsets(); public ViewGroup.LayoutParams lp; public Runnable delayed;
 public View(Context c){context=c;}
 public Context getContext(){return context;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}
 public int getWidth(){return width;} public int getHeight(){return height;}
 public float getAlpha(){return alpha;} public void setAlpha(float a){alpha=a;}
 public void setVisibility(int v){shown=v==VISIBLE;} public boolean isShown(){return shown;}
 public int getWindowVisibility(){return windowVisibility;}
 public boolean isAttachedToWindow(){return attached;}
 public ViewParent getParent(){return parent;}
 public View getRootView(){return parent==null?this:parent.getRootView();}
 public ViewGroup.LayoutParams getLayoutParams(){return lp;}
 public WindowInsets getRootWindowInsets(){return insets;}
 public void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}
 public boolean getGlobalVisibleRect(android.graphics.Rect r){return unclipped;}
 public boolean postDelayed(Runnable r,long ms){delayed=r;return true;}
 public boolean removeCallbacks(Runnable r){return true;}
 public void invalidate(){invalidations++;}
 protected void onDraw(android.graphics.Canvas c){}
}
'@
Write-Source 'android/view/ViewGroup.java' @'
package android.view;
public class ViewGroup extends View {
 public int ink; public View host;
 public ViewGroup(android.content.Context c){super(c);}
 public static class LayoutParams {}
}
'@
Write-Source 'android/view/WindowManager.java' @'
package android.view;
public class WindowManager {
 public int adds,updates,removes; public boolean failNext,failAdd; public View glyph;
 public void addView(View v,LayoutParams p){if(failAdd)throw new IllegalStateException("add");adds++;glyph=v;}
 public void updateViewLayout(View v,LayoutParams p){updates++;if(failNext){failNext=false;throw new IllegalStateException("placement");}}
 public void removeViewImmediate(View v){removes++;}
 public static class LayoutParams extends ViewGroup.LayoutParams {
  public static final int FLAG_NOT_FOCUSABLE=8,FLAG_NOT_TOUCHABLE=16,FLAG_LAYOUT_IN_SCREEN=256,FLAG_LAYOUT_NO_LIMITS=512;
  public static final int TYPE_STATUS_BAR_SUB_PANEL=2017,TYPE_STATUS_BAR_ADDITIONAL=2041,PRIVATE_FLAG_TRUSTED_OVERLAY=1<<29;
  public int type,gravity,width,height,format,flags,privateFlags,windowAnimations,x,y; public float alpha=1;
  public void setTitle(String s){}
 }
}
'@
$pkg = 'io/github/yixing233/hyperduo'
Write-Source "$pkg/TrioConfig.java" @'
package io.github.yixing233.hyperduo;
final class TrioConfig {
 static TrioSettings settings=TrioSettings.defaults();
 static TrioSettings get(){return settings;} static TrioAppearance appearance(){return TrioAppearance.of(settings);}
}
'@
Write-Source "$pkg/TrioRenderer.java" @'
package io.github.yixing233.hyperduo;
final class TrioRenderer {
 static void drawState(android.graphics.Canvas c,int w,int h,TrioState s,TrioSettings cfg){}
 static boolean designTransform(float w,float h,TrioAppearance a,float[] t){throw new AssertionError("not a geometry test");}
}
'@
Write-Source "$pkg/Refl.java" @'
package io.github.yixing233.hyperduo;
final class Refl {
 static int getInt(Object f,Object h,int v){return v;} static boolean getBool(Object f,Object h,boolean v){return v;}
 static float getFloat(Object f,Object h,float v){return v;}
 static Class<?> cls(String n,ClassLoader c){return Object.class;} static Object method(Class<?> c,String n,Class<?>... args){return n;}
}
'@
Write-Source "$pkg/TrioState.java" @"
package io.github.yixing233.hyperduo;
import android.view.View;
import android.os.SystemClock;
final class TrioState {
 final View host; TrioState(View v){host=v;}
 int level=79,wifiLevel=-1,mobileLevel=-1,sims; int[] slotLevels={-1,-1};
 boolean charging,quickCharging,low,powerSave,performanceMode,useTint,wifiPresent,islandHideBattery;
 int tintColor,lightColor=0xFFFFFFFF,darkColor=0xFF000000; float darkIntensity; String mobileType="";
 static Object fLevel,fCharging,fQuick,fLow,fPowerSave,fPerformance,fUseTint,fTint,fLight,fDark,fIntensity;
 static int sWifiLevel=-1,sMobileLevel=-1,sDataSlot=0; static boolean sWifiPresent,sIslandHideBattery;
 static String sMobileType=""; static int[] sSlotLevels={-1,-1}; static int nextLevel=2,polls;
 static long sSimsDueAt; static final long SIMS_INTERVAL_MS=2000; static final int SIM_SLOTS=2,DEFAULT_FOREGROUND=0xFF000000;
 static Object sContext;
 static void ensureFields(Class<?> c){} static void resolvePending(Object r){} static Object resourcesOf(View v){return null;}
 static void sampleSims(Object c){polls++;sSlotLevels=new int[]{nextLevel,-1};}
 void noteTint(int ink){} boolean nightMode(){return false;}
 $refresh
 $poll
 $foreground
}
"@
Write-Source "$pkg/TrioHooks.java" @"
package io.github.yixing233.hyperduo;
import android.view.*;
import java.util.*;
final class XposedModule {}
final class XposedInterface {
 interface Hooker {Object intercept(Chain c) throws Throwable;}
 interface Chain {Object proceed();Object getThisObject();Object getArg(int i);}
}
final class TrioHooks {
 static final int LOG_INFO=1,LOG_WARN=2;
 static final Map<View,Integer> ROW_INK=new WeakHashMap<>();
 static XposedInterface.Hooker tintHook;
 static boolean covered,island; static View bar;
 static void log(int level,String msg){} static void log(XposedModule m,String msg){}
 static void nudgeHosts(){} static void invalidateHosts(){}
 static Object statusIconContainer(){return null;} static View statusBarView(){return bar;}
 static boolean isStatusBarHost(View v){return true;}
 static boolean barCovered(){return covered;} static boolean islandHideBattery(){return island;}
 static View batteryContainerOf(View v){return (View)v.getParent();}
 static View rowOf(View v){return batteryContainerOf(v);}
 static View hostIn(ViewGroup row){return row.host;}
 static void applyOutRingInk(ViewGroup row,int ink){row.ink=ink;}
 static int hook(XposedModule m,Object method,String name,XposedInterface.Hooker h){tintHook=h;return 1;}
 $rowInk
 $meter
 static void tint(View meter,float intensity,boolean useTint,int tint)throws Throwable {
  if(tintHook==null)hookMeterTint(new XposedModule(),TrioHooks.class.getClassLoader());
  Object[] args={new ArrayList<>(),intensity,tint,0xFFFFFFFF,0xFF000000,useTint};
  tintHook.intercept(new XposedInterface.Chain(){
   public Object proceed(){return null;} public Object getThisObject(){return meter;} public Object getArg(int i){return args[i];}
  });
 }
}
"@
Write-Source "$pkg/IssueRegressionTest.java" @'
package io.github.yixing233.hyperduo;
import android.content.Context;
import android.view.*;
public class IssueRegressionTest {
 static int checks;
 static void ok(String message,boolean value){if(!value)throw new AssertionError(message);checks++;}
 static View host(Context c){ViewGroup row=new ViewGroup(c);View v=new View(c);v.parent=row;row.host=v;return v;}
 static void overlay() {
  Context c=new Context();View h=host(c);TrioState s=new TrioState(h);
  TrioOverlay o=TrioOverlay.active(h,s);
  ok("adding a 1px window must not suppress the host",!o.covering());
  c.wm.failNext=true;o.sync();
  ok("failed placement must leave host painting",!o.covering() && c.wm.glyph.getAlpha()==0);
  int redraws=h.invalidations;c.wm.glyph.delayed.run();
  ok("same desired params must retry and then cover",c.wm.updates==2 && o.covering());
  ok("watcher recovery redraws the fallback host",h.invalidations==redraws+1);
  redraws=h.invalidations;o.sync();ok("stable placement must avoid another IPC",c.wm.updates==2);
  ok("stable placement does not loop host redraws",h.invalidations==redraws);
  h.x-=10;c.wm.failNext=true;c.wm.glyph.delayed.run();
  ok("later placement failure also gives host back",!o.covering());
  ok("watcher failure requests fallback host drawing",h.invalidations==redraws+1);
  redraws=h.invalidations;c.wm.glyph.delayed.run();ok("later failure recovers",o.covering() && c.wm.updates==4);
  ok("second watcher recovery clears fallback ink",h.invalidations==redraws+1);
  for(int gate=0;gate<6;gate++) {
   o.hideNow();
   switch(gate){case 0:h.insets.visible=false;break;case 1:h.insets.top=0;break;
    case 2:TrioHooks.covered=true;break;case 3:TrioHooks.island=true;break;
    case 4:h.windowVisibility=4;break;case 5:h.unclipped=false;break;}
   o.showAgain();ok("settling must obey visibility gate "+gate,!o.covering());
   h.insets.visible=true;h.insets.top=121;TrioHooks.covered=false;TrioHooks.island=false;h.windowVisibility=0;h.unclipped=true;
   o.showAgain();ok("restoring visibility gate "+gate,o.covering());
  }
  TrioOverlay.onShadeHeight(10);ok("shade hides immediately",!o.covering());
  h.insets.visible=false;TrioOverlay.onShadeHeight(0);ok("shade settle respects fullscreen",!o.covering());
  h.insets.visible=true;o.showAgain();
  TrioOverlay.release(h);o.showAgain();ok("released window stays released",!o.covering() && c.wm.removes==1);
  c.wm.failAdd=true;ok("failed add returns host path",TrioOverlay.active(h,s)==null);
  c.wm.failAdd=false;o=TrioOverlay.active(h,s);o.sync();ok("failed add can recover",o.covering());TrioOverlay.release(h);
 }
 static void signal() {
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.dualSim=false;
  TrioState.sMobileLevel=-1;TrioState.sSlotLevels=new int[]{-1,-1};TrioState.sSimsDueAt=0;TrioState.polls=0;
  TrioState s=new TrioState(host(new Context()));TrioState.nextLevel=2;s.refresh();
  ok("initial telephony fallback",s.mobileLevel==2 && TrioState.polls==1);
  TrioState.nextLevel=4;android.os.SystemClock.now+=1999;s.refresh();
  ok("fallback retains the two-second rate limit",s.mobileLevel==2 && TrioState.polls==1);
  android.os.SystemClock.now++;s.refresh();
  ok("fallback refreshes after first successful reading",s.mobileLevel==4 && TrioState.polls==2);
  TrioState.sMobileLevel=3;android.os.SystemClock.now+=2000;s.refresh();
  ok("known icon source skips unnecessary telephony polling",s.mobileLevel==3 && TrioState.polls==2);
 }
 static void tint() throws Throwable {
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
  View a=host(new Context()),b=host(new Context());ViewGroup ar=(ViewGroup)a.parent,br=(ViewGroup)b.parent;
  TrioHooks.ROW_INK.put(ar,0xFFFFFFFF);TrioHooks.ROW_INK.put(br,0xFFFFFFFF);
  for(float intensity:new float[]{0f,0.5f,1f}) {
   TrioHooks.tint(a,intensity,false,0);
   int want=intensity>0?0xFF000000:0xFFFFFFFF;
   ok("meter keeps fractional intensity "+intensity,ar.ink==want);
   ok("meter archives latest ink "+intensity,TrioHooks.rowInk(ar)==want);
   ok("draw and meter agree "+intensity,new TrioState(a).foreground()==want);
   ok("other row remains independent",TrioHooks.rowInk(br)==0xFFFFFFFF);
  }
  TrioConfig.settings.mobileTypeMode=Prefs.MOBILE_TYPE_OFF;
  TrioHooks.tint(a,0f,true,0xFF123456);TrioHooks.tint(b,0f,true,0xFF654321);
  ok("type disabled still receives tint",TrioHooks.rowInk(ar)==0xFF123456 && ar.ink==0xFF123456);
  ok("interleaved row tint remains local",TrioHooks.rowInk(br)==0xFF654321 && TrioHooks.rowInk(ar)==0xFF123456);
  int before=a.invalidations;TrioHooks.tint(a,0f,true,0xFF123456);
  ok("unchanged tint avoids host redraw",a.invalidations==before);
 }
 static void wifi() {
  TrioSettings s=TrioSettings.defaults();s.showValue=false;s.mobileTypeMode=Prefs.MOBILE_TYPE_OFF;s.dualSim=false;
  ok("no Wi-Fi reading leaves the middle empty",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,-1,"5G",2)==null);
  ok("a live Wi-Fi reading still names the arcs",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,3,"5G",2)==TrioZones.Part.WIFI);
  s.showWifi=false;
  ok("the Wi-Fi switch still controls live arcs",TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(s),79,false,3,"5G",2)==null);
 }
 static void clear() {
  for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_RECT}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;s.showValue=false;s.valueCentred=true;
   s.dualSim=false;s.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
   for(TrioZones.Zone zone:TrioZones.Zone.values()) {
    TrioZones.Assignment a=TrioZones.assign(zone,style==Prefs.STYLE_RECT,null,s,2);
    ok("zone can be cleared "+style+"/"+zone,a!=null);
    for(boolean charging:new boolean[]{false,true})for(int wifi:new int[]{-1,3}) {
     ok("cleared zone stays empty "+style+"/"+zone+"/"+charging+"/"+wifi,
      TrioZones.occupant(zone,TrioAppearance.of(a.settings),79,charging,wifi,"5G",2)==null);
    }
   }
  }
  // Independent exhaustive check for clearing the middle when both the battery
  // number and the no-Wi-Fi type compete with the arcs. The implementation searches
  // a restricted candidate set; compare its answer with every legal switch edit.
  TrioSettings cur=TrioSettings.defaults();cur.mobileTypeMode=Prefs.MOBILE_TYPE_IN_RING;
  TrioZones.Assignment solved=TrioZones.assign(TrioZones.Zone.CENTRE,false,null,cur,2);
  int minimum=Integer.MAX_VALUE;
  for(int bits=0;bits<64;bits++)for(int mode:new int[]{Prefs.MOBILE_TYPE_OFF,Prefs.MOBILE_TYPE_IN_RING}) {
   TrioSettings c=cur.copy();c.showWifi=(bits&1)!=0;c.showValue=(bits&2)!=0;c.valueCentred=(bits&4)!=0;
   c.showBolt=(bits&8)!=0;c.dualSim=(bits&16)!=0;c.showMobile=(bits&32)!=0;c.mobileTypeMode=mode;
   boolean empty=true;
   for(boolean charging:new boolean[]{false,true})for(int wifi:new int[]{-1,3}) {
    empty &= TrioZones.occupant(TrioZones.Zone.CENTRE,TrioAppearance.of(c),79,charging,wifi,"5G",2)==null;
   }
   if(empty) {
    int changes=(c.showWifi!=cur.showWifi?1:0)+(c.showValue!=cur.showValue?1:0)
     +(c.valueCentred!=cur.valueCentred?1:0)+(c.showBolt!=cur.showBolt?1:0)
     +(c.dualSim!=cur.dualSim?1:0)+(c.showMobile!=cur.showMobile?1:0)+(c.mobileTypeMode!=cur.mobileTypeMode?1:0);
    minimum=Math.min(minimum,changes);
   }
  }
  ok("clearing all four frames uses the smallest legal edit",solved!=null && solved.changes==minimum);
 }
 static void assignment() {
  for(int style:new int[]{Prefs.STYLE_RING,Prefs.STYLE_RECT}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;s.mobileTypeMode=Prefs.MOBILE_TYPE_OUT_RING;
   TrioZones.Assignment a=TrioZones.assign(TrioZones.Zone.BOTTOM,style==Prefs.STYLE_RECT,TrioZones.Part.MOBILE,s,2);
   ok("selecting existing mobile leaves outside type unchanged "+style,a!=null && a.changes==0 && a.settings.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
   a=TrioZones.assign(TrioZones.Zone.BOTTOM,style==Prefs.STYLE_RECT,null,s,2);
   ok("clearing mobile preserves outside type "+style,a!=null && a.changes==1 && !a.settings.showMobile && a.settings.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
  }
  TrioSettings s=TrioSettings.defaults();s.dualSim=false;
  ok("preview's two readings can enable dual rows",TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.DUAL,s,2)!=null);
 }
 public static void main(String[] args)throws Throwable {
  switch(args[0]){case "overlay":overlay();break;case "signal":signal();break;case "tint":tint();break;case "assignment":assignment();break;case "wifi":wifi();break;case "clear":clear();break;default:throw new AssertionError(args[0]);}
  System.out.println("PASS: "+args[0]+" / "+checks+" assertions");
 }
}
'@
& $javac -nowarn -encoding UTF-8 -d $classes $files
if ($LASTEXITCODE) { throw 'Issue regression harness compilation failed' }
foreach ($suite in $Suite) {
    & $java -Xmx128m -cp $classes io.github.yixing233.hyperduo.IssueRegressionTest $suite
    if ($LASTEXITCODE) { throw "Issue regression failed: $suite" }
}
# Kotlin/Compose is compiled by the APK build. This pin only guards the bridge
# between the synthetic renderer readings and the Java solver; it does not test UI gestures.
$drawing = [IO.File]::ReadAllText((Join-Path $trio 'ui/DrawingScreen.kt'))
if ($drawing -notmatch 'val sims = scene\.previewSlots\.count \{ it >= 0 \}' -or
    $drawing -notmatch 'scene\.previewSlots, PREVIEW_TYPE') { throw 'Preview and solver must share their SIM readings' }
Write-Output 'PASS: preview SIM wiring source contract'
# These pins check the Compose bridge only; recogniser timing and dialog rendering
# still require Android. The runtime occupant used here is executed in wifi/clear.
$glyph = [regex]::Match($drawing, '(?s)private fun DrawingGlyph\(.*?\n\}').Value
if (!$glyph -or $glyph -notmatch 'currentEdit = rememberUpdatedState\(onEditZone\)' -or
    $glyph -notmatch 'onLongPress = \{ offset -> zoneAt\(offset\)\?\.let \{ currentEdit\.value\(it\) \} \}') {
    throw 'A glyph long press must use the current edit callback'
}
if ($drawing -notmatch '(?s)onEditZone = \{ zone ->.*?TrioZones\.occupant\(.*?if \(part == null\) \{\s*picking = zone\s*\} else \{\s*editing = TrioSettingsPart\.of\(part\)') {
    throw 'Long press must edit the visible component or pick one for an empty zone'
}
if ($drawing -notmatch 'applyZoneAssignment\(solved\.settings, r, settings\)' -or
    $drawing -notmatch 'lastSelection = part\?\.let \{ ZoneSelection\(pickZone, it\) \}' -or
    $drawing -notmatch 'PlacementFeedback\(selection, appearance, scene, sims\)' -or
    $drawing -notmatch 'if \(actual == selection\.part\) return') {
    throw 'Selection feedback must compare the applied choice with this preview frame'
}
foreach ($name in @('charging','bolt','wifi','other')) {
    if ($drawing -notmatch "R\.string\.drawing_feedback_$name") { throw "Missing preview feedback: $name" }
}
Write-Output 'PASS: long-press and preview-feedback source contracts (Android gestures not executed)'
