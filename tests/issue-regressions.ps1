param(
    [string]$SourceDirectory,
    [ValidateSet('signal','tint','assignment','wifi','clear','clock','centre','dots','outorder','capsule','isolation')]
    [string[]]$Suite = @('signal','tint','assignment','wifi','clear','clock','centre','dots','outorder','capsule','isolation')
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
# Execute the real tint/clock hooks. Android services, Xposed dispatch and
# telephony are faked. refresh + its polling guard are spliced
# unchanged so a successful fallback cannot hide the next polling decision.
$state = [IO.File]::ReadAllText((Join-Path $SourceDirectory 'TrioState.java'))
$hooks = [IO.File]::ReadAllText((Join-Path $SourceDirectory 'TrioHooks.java'))
$refresh = Read-Method $state 'void refresh()'
$poll = Read-Method $state 'private static void sampleSimsIfDue()'
$foreground = Read-Method $state 'int foreground()'
$meter = Read-Method $hooks 'private static int hookMeterTint(XposedModule module, ClassLoader cl)'
$iconTint = Read-Method $hooks 'private static int hookIconTint(XposedModule module, ClassLoader cl)'
$rowInk = Read-Method $hooks 'static int rowInk(View row)'
$clockVisibility = Read-Method $hooks 'static void applyClockVisibility()'
foreach ($name in @('TrioZones.java','TrioTint.java','OutRingLayout.java')) {
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
Write-Source 'android/graphics/Rect.java' @'
package android.graphics;
public class Rect {
 public int left,top,right,bottom;
 public Rect(){} public Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public Rect(Rect r){this(r.left,r.top,r.right,r.bottom);}
 public boolean isEmpty(){return left>=right || top>=bottom;}
}
'@
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
import java.lang.reflect.Method;
final class XposedModule {}
final class XposedInterface {
 interface Hooker {Object intercept(Chain c) throws Throwable;}
 interface Chain {Object proceed();Object getThisObject();Object getArg(int i);default List<Object> getArgs(){return Collections.emptyList();}}
}
final class TrioHooks {
 static final int LOG_INFO=1,LOG_WARN=2;
 static final Map<View,Integer> ROW_INK=new WeakHashMap<>();
 static final Map<View,TrioTint.Inputs> ROW_TINTS=new WeakHashMap<>();
 static final Map<String,XposedInterface.Hooker> hooks=new HashMap<>();
 static XposedInterface.Hooker tintHook;
 static XposedModule sModule=new XposedModule();
 static View sStatusClock; static java.lang.reflect.Method sClockSetter; static boolean sClockHidden;
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
 static int hook(XposedModule m,Object method,String name,XposedInterface.Hooker h){hooks.put(name,h);if(name.equals("hyperduo-meter-tint"))tintHook=h;return 1;}
 $rowInk
 $meter
 $iconTint
 $clockVisibility
 static void tint(View meter,float intensity,boolean useTint,int tint)throws Throwable {
  tint(meter,new ArrayList<>(),intensity,useTint,tint);
 }
 static void tint(View meter,ArrayList<android.graphics.Rect> areas,float intensity,boolean useTint,int tint)throws Throwable {
  if(tintHook==null)hookMeterTint(new XposedModule(),TrioHooks.class.getClassLoader());
  Object[] args={areas,intensity,tint,0xFFFFFFFF,0xFF000000,useTint};
  tintHook.intercept(new XposedInterface.Chain(){
   public Object proceed(){return null;} public Object getThisObject(){return meter;} public Object getArg(int i){return args[i];}
  });
 }
 static void iconTint(View icon,ArrayList<android.graphics.Rect> areas,int colour)throws Throwable {
  hookIconTint(new XposedModule(),TrioHooks.class.getClassLoader());
  List<Object> args=Arrays.asList(areas,0.5f,colour);
  hooks.get("hyperduo-tint-dark").intercept(new XposedInterface.Chain(){
   public Object proceed(){return null;}public Object getThisObject(){return icon;}
   public Object getArg(int i){return args.get(i);}public List<Object> getArgs(){return args;}
  });
 }
}
"@
Write-Source "$pkg/IssueRegressionTest.java" @'
package io.github.yixing233.hyperduo;
import android.content.Context;
import android.view.*;
import java.util.Arrays;
public class IssueRegressionTest {
 static int checks;
 static void ok(String message,boolean value){if(!value)throw new AssertionError(message);checks++;}
 static View host(Context c){ViewGroup row=new ViewGroup(c);View v=new View(c);v.parent=row;row.host=v;return v;}
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
  java.util.ArrayList<android.graphics.Rect> areas=new java.util.ArrayList<>();
  android.graphics.Rect area=new android.graphics.Rect(0,0,500,100);areas.add(area);
  for(boolean use:new boolean[]{false,true}) {
   TrioHooks.tint(a,areas,0.5f,use,0xFF123456);
   ok("outside dark area stays light "+use,TrioHooks.rowInk(ar)==0xFFFFFFFF && ar.ink==0xFFFFFFFF);
  }
  area.left=850;area.right=1000;
  TrioHooks.tint(a,areas,0.5f,true,0xFF123456);
  ok("inside tint area uses tint",TrioHooks.rowInk(ar)==0xFF123456);
  area.left=0;area.right=500;areas.clear();
  ok("cached area owns a copy of list and rect",TrioHooks.rowInk(ar)==0xFF123456);
  a.x=0;
  ok("host movement re-evaluates cached area",TrioHooks.rowInk(ar)==0xFFFFFFFF);
  TrioHooks.iconTint(a,new java.util.ArrayList<>(),0xFF000000);
  ok("neighbour tint cannot overwrite meter area",TrioHooks.rowInk(ar)==0xFFFFFFFF);
  View c=host(new Context());ViewGroup cr=(ViewGroup)c.parent;
  areas.add(new android.graphics.Rect(0,0,500,100));
  TrioHooks.iconTint(c,areas,0xFF000000);
  ok("icon-only fallback respects battery area",TrioHooks.rowInk(cr)==0xFFFFFFFF);
  TrioHooks.tint(c,0.5f,true,0xFF654321);
  TrioHooks.iconTint(c,areas,0xFF000000);
  ok("meter dispatch wins in both callback orders",TrioHooks.rowInk(cr)==0xFF654321);
  areas.clear();areas.add(new android.graphics.Rect(900,0,930,100));
  TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("exactly half coverage stays light like native",TrioHooks.rowInk(cr)==0xFFFFFFFF);
  areas.get(0).right=931;TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("more than half coverage uses tint",TrioHooks.rowInk(cr)==0xFF000000);
  areas.get(0).top=1;TrioHooks.tint(c,areas,1f,true,0xFF000000);
  ok("area below the bar does not tint",TrioHooks.rowInk(cr)==0xFFFFFFFF);
 }
 static void wifi() {
  // A fresh install starts blank, so this places the arcs explicitly - the point of the
  // case is the READING, not the default layout.
  TrioSettings s=TrioSettings.defaults();s.showValue=false;s.mobileTypeMode=Prefs.MOBILE_TYPE_OFF;s.dualSim=false;
  s.ringZones=Prefs.decodeLayout("|1|");
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
  // Ring: placement is a priority list, so "clear" empties that zone's list and leaves the
  // switch alone - the placement lives in the layout, not in the switch.
  {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   s.mobileTypeMode=Prefs.MOBILE_TYPE_OUT_RING;
   TrioSettings placed=TrioZones.withPiece(s,TrioZones.Zone.BOTTOM.ordinal(),TrioZones.Part.MOBILE);
   ok("placing mobile leaves outside type unchanged",placed.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
   ok("the placed piece heads the zone's list",
      placed.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_MOBILE);
   TrioSettings cleared=TrioZones.withPiece(placed,TrioZones.Zone.BOTTOM.ordinal(),null);
   ok("clearing empties the zone, not the switch",
      cleared.ringZones[TrioZones.Zone.BOTTOM.ordinal()].length==0 && cleared.showMobile);
   // Dropping one entry is a different operation and reveals the candidate behind it.
   TrioSettings two=TrioZones.withPiece(placed,TrioZones.Zone.BOTTOM.ordinal(),TrioZones.Part.WIFI);
   ok("a second piece goes to the front",
      two.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_WIFI);
   TrioSettings one=TrioZones.withoutAt(two,TrioZones.Zone.BOTTOM.ordinal(),0);
   ok("dropping the head reveals the next candidate",
      one.ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==Prefs.PART_MOBILE);
  }
  // Rectangle: still one switch per zone, and the solver still answers.
  {
   int style=Prefs.STYLE_RECT;
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;s.mobileTypeMode=Prefs.MOBILE_TYPE_OUT_RING;
   TrioZones.Assignment a=TrioZones.assign(TrioZones.Zone.BOTTOM,true,TrioZones.Part.MOBILE,s,2);
   ok("rect: selecting existing mobile leaves outside type unchanged",a!=null && a.changes==0 && a.settings.mobileTypeMode==Prefs.MOBILE_TYPE_OUT_RING);
   a=TrioZones.assign(TrioZones.Zone.BOTTOM,true,null,s,2);
   ok("rect: clearing mobile switches it off",a!=null && a.changes==1 && !a.settings.showMobile);
  }
  TrioSettings s=TrioSettings.defaults();s.dualSim=false;
  // Two cards are two placed pieces now, not a second row of the aggregate meter, so
  // what has to be reachable is each card's own component.
  ok("either card's row can be placed",TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_1,s,2)!=null
     && TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_2,s,2)!=null);
 }
 public static class NativeClock extends View {
  int calls,value;boolean fail=true;
  NativeClock(){super(new Context());}
  public void setMultiTaskVisibility(int visibility){calls++;if(fail){fail=false;throw new IllegalStateException("transient clock setter failure");}value=visibility;}
 }
 static void clock()throws Exception {
  NativeClock clock=new NativeClock();TrioHooks.sStatusClock=clock;
  TrioHooks.sClockSetter=NativeClock.class.getDeclaredMethod("setMultiTaskVisibility",int.class);
  TrioHooks.sClockSetter.setAccessible(true);TrioHooks.sClockHidden=false;
  TrioConfig.settings=TrioSettings.defaults();TrioConfig.settings.trioStyle=Prefs.STYLE_CAPSULE;
  TrioHooks.applyClockVisibility();
  ok("failed clock hide is not cached as success",!TrioHooks.sClockHidden && clock.calls==1);
  TrioHooks.applyClockVisibility();
  ok("failed clock hide retries",TrioHooks.sClockHidden && clock.calls==2 && clock.value==8);
  TrioHooks.applyClockVisibility();ok("stable clock hiding is idempotent",clock.calls==2);
  TrioConfig.settings.trioStyle=Prefs.STYLE_RING;TrioHooks.applyClockVisibility();
  ok("leaving capsule restores native clock",!TrioHooks.sClockHidden && clock.value==0);
  TrioConfig.settings.trioStyle=Prefs.STYLE_CAPSULE;TrioHooks.applyClockVisibility();
  TrioConfig.settings.enabled=false;TrioHooks.applyClockVisibility();
  ok("master off restores native clock",!TrioHooks.sClockHidden && clock.value==0);
 }
 /**
  * A signal row may not be placed in the ring's middle band, and every path that could
  * place one refuses it there.
  *
  * <p>The rule exists because the middle band has no opening for the row to sit in: the
  * arc runs past both sides, so four beads there are a horizontal smudge across a closed
  * ring rather than the ring cut into segments. The test is stated against the three
  * doors a placement can come through - the layout write, the solver and the picker's own
  * list - because a rule enforced at one of them is a rule the other two can walk around.
  */
 static void centre() {
  for(TrioZones.Part row:new TrioZones.Part[]{
      TrioZones.Part.MOBILE,TrioZones.Part.SIM_1,TrioZones.Part.SIM_2}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   ok("the ring refuses "+row+" in the middle",!TrioZones.ringAccepts(TrioZones.Zone.CENTRE,row));
   ok("and accepts it at the top",TrioZones.ringAccepts(TrioZones.Zone.TOP,row));
   ok("and at the bottom",TrioZones.ringAccepts(TrioZones.Zone.BOTTOM,row));
   // Door 1: the layout write hands the settings back untouched rather than storing a
   // placement the drawing would skip.
   TrioSettings refused=TrioZones.withPiece(s,TrioZones.Zone.CENTRE.ordinal(),row);
   ok("withPiece refuses "+row+" in the middle",
      refused.ringZones[TrioZones.Zone.CENTRE.ordinal()].length==0);
   ok("withPiece does not mutate on refusal",s.ringZones[TrioZones.Zone.CENTRE.ordinal()].length==0);
   ok("withPiece still places "+row+" at the bottom",
      TrioZones.withPiece(s,TrioZones.Zone.BOTTOM.ordinal(),row)
        .ringZones[TrioZones.Zone.BOTTOM.ordinal()][0]==TrioZones.encodePart(row));
   // Door 2: the solver reports it as unsolvable, so the page's row is disabled.
   ok("the solver refuses "+row+" in the middle",
      TrioZones.assign(TrioZones.Zone.CENTRE,false,row,s,2)==null);
   ok("the solver places "+row+" at the bottom",
      TrioZones.assign(TrioZones.Zone.BOTTOM,false,row,s,2)!=null);
   // Door 3: the picker never offers it, so there is no dead menu entry. MOBILE is the
   // retired aggregate meter and is absent from every list by construction - its own
   // absence is asserted elsewhere - so only the two live rows are checked here.
   ok("the picker does not offer "+row+" in the middle",
      !TrioZones.placementChoices(TrioZones.Zone.CENTRE,s,2).contains(row));
   if(row!=TrioZones.Part.MOBILE) {
    ok("the picker offers "+row+" at the bottom",
       TrioZones.placementChoices(TrioZones.Zone.BOTTOM,s,2).contains(row));
   }
   // And the drawing passes over a layout that still names it, so a snapshot delivered
   // And the drawing passes over a layout that still names it. Decoding repairs such a
   // layout today, so this asserts the drawing's own guard by handing it the raw arrays a
   // broadcast could carry - a snapshot built by a process that never decoded a string.
   // Two shapes, because the guard must do both: skip the refused head, and let the
   // candidate behind it take the band rather than leaving it blank.
   int code=TrioZones.encodePart(row);
   for(int[] chain:new int[][]{{code},{code,Prefs.PART_WIFI}}) {
    TrioSettings stale=s.copy();
    stale.showSim1=stale.showSim2=true;stale.showMobile=true;stale.showWifi=true;
    // Index 1 is the middle band; 0 and 2 are the openings.
    stale.ringZones=new int[][]{{},chain,{}};
    int slot=TrioAppearance.of(stale).ring(79,false,3,"5G",2)
      .parts[TrioZones.Zone.CENTRE.ordinal()];
    ok("the drawing skips a stored middle "+row+" in "+Arrays.toString(chain),
       slot!=code);
    ok("and takes the fallback behind it, not a blank band "
       +Arrays.toString(chain),
       chain.length==1 ? slot==Prefs.PART_NONE : slot==Prefs.PART_WIFI);
   }
  }
  // Everything else keeps the middle, so the rule is the signal's and not a blanket ban.
  for(TrioZones.Part part:TrioZones.Part.values()) {
   if(TrioZones.isSignalRow(part)) continue;
   TrioSettings s=TrioSettings.defaults();s.trioStyle=Prefs.STYLE_RING;
   ok("the middle still takes "+part,TrioZones.ringAccepts(TrioZones.Zone.CENTRE,part));
   ok("and the solver places "+part+" there",
      TrioZones.assign(TrioZones.Zone.CENTRE,false,part,s,2)!=null);
  }
  // Decoding a layout written before the rule moves the row to the bottom band instead of
  // deleting it: dropping it would read as "my setting was lost". The middle holds a
  // reading ahead of the row here, so the rest of that band has to survive untouched.
  int[][] decoded=Prefs.decodeLayout("|3,7|");
  ok("a stored middle signal row leaves the middle",
     !Arrays.equals(decoded[Prefs.ZONE_CENTRE],new int[]{Prefs.PART_VALUE,Prefs.PART_SIM_1}));
  ok("and arrives in the bottom band",
     Arrays.equals(decoded[Prefs.ZONE_BOTTOM],new int[]{Prefs.PART_SIM_1}));
  ok("the rest of the middle survives, in order",
     Arrays.equals(decoded[Prefs.ZONE_CENTRE],new int[]{Prefs.PART_VALUE}));
  // Appended behind whatever the bottom band already held, so a deliberately promoted
  // piece keeps its precedence.
  ok("a moved row goes behind the bottom band's own head",
     Arrays.equals(Prefs.decodeLayout("|7|1")[Prefs.ZONE_BOTTOM],
        new int[]{Prefs.PART_WIFI,Prefs.PART_SIM_1}));
  // A row already in the bottom band is not duplicated by the move.
  ok("a row already at the bottom is not duplicated",
     Arrays.equals(Prefs.decodeLayout("|7|7")[Prefs.ZONE_BOTTOM],
        new int[]{Prefs.PART_SIM_1}));
  // Repair is idempotent: running it twice must not move anything twice.
  int[][] once=TrioZones.repairRingLayout(Prefs.decodeLayout("|7|1"));
  ok("repairing a repaired layout changes nothing",
     Arrays.deepEquals(once,TrioZones.repairRingLayout(once)));
  // The other two arrangements have no such band, so their middle keeps everything.
  for(int style:new int[]{Prefs.STYLE_RECT,Prefs.STYLE_CAPSULE}) {
   TrioSettings s=TrioSettings.defaults();s.trioStyle=style;
   for(TrioZones.Part row:new TrioZones.Part[]{
       TrioZones.Part.MOBILE,TrioZones.Part.SIM_1,TrioZones.Part.SIM_2}) {
    ok("style "+style+" keeps "+row+" in its middle",
       TrioZones.accepts(TrioZones.Zone.CENTRE,row,s));
   }
  }
 }
 /**
  * The ring's level-dot size setting, which has to scale the bead without letting it
  * outgrow the ink box.
  *
  * <p>Two claims, and both are needed. The first is that the slider does something the
  * drawing can see: the bead radius has to follow it. The second is the ceiling - the
  * beads are threaded onto the ring's own arc, so a larger bead reaches further from the
  * ring centre, and the ink box is what fits the glyph to its view. A setting that pushed
  * ink past that box would be clipped by the host and, worse, would make the glyph grow
  * and shrink as the slider moved.
  *
  * <p>The default is asserted to be a no-op, because that is the whole compatibility
  * promise: an install that never touches the key must keep the bead whose diameter is the
  * ring's thickness.
  */
 static void dots() {
  // 1. 100% is exactly the old radius, at every stroke, for both end thirds.
  for(int stroke=Prefs.MIN_RING_STROKE;stroke<=Prefs.MAX_RING_STROKE;stroke++) {
   float width=78f-stroke;
   float legacy=TrioGeometry.arcRowDotR(0,stroke,width);
   float at100=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   ok("100% is the old bead at stroke "+stroke,Math.abs(legacy-at100)<1e-6f);
   // And the two openings share the size: they are one piece of ink.
   ok("the top and bottom rows share one size at stroke "+stroke,
      Math.abs(TrioGeometry.arcRowDotR(0,stroke,width,1f)
             -TrioGeometry.arcRowDotR(2,stroke,width,1f))<1e-6f);
  }
  // 2. Smaller really is smaller, and larger is larger - until the box stops it.
  {
   int stroke=Prefs.DEF_RING_STROKE;
   float width=78f-stroke;
   float small=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MIN_DOT_SCALE/100f);
   float normal=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   float big=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MAX_DOT_SCALE/100f);
   ok("the smallest setting draws a smaller bead",small<normal);
   ok("the largest setting draws a larger bead",big>normal);
   ok("the bead never shrinks below a drawable radius",small>=0.5f);
  }
  // 3. The ceiling: no setting, at any stroke, may ask for a bead the box cannot hold,
  // and none may ask for one the opening cannot hold either. Both limits are real - the
  // box stops the row leaving the glyph, the opening stops it overrunning the arc's caps -
  // and whichever binds first is the one the drawing must obey.
  for(int stroke=Prefs.MIN_RING_STROKE;stroke<=Prefs.MAX_RING_STROKE;stroke++) {
   float width=78f-stroke;
   for(int pct:new int[]{Prefs.MIN_DOT_SCALE,Prefs.DEF_DOT_SCALE,Prefs.MAX_DOT_SCALE}) {
    float r=TrioGeometry.arcRowDotR(0,stroke,width,pct/100f);
    ok("stroke "+stroke+" at "+pct+"% stays inside the ink box",
       r<=TrioGeometry.maxBeadR()+0.01f);
    // The reference bead is what the opening was designed around; the test asserts the
    // drawn radius never exceeds the roomier of the two mechanical limits, which is what
    // keeps a future change to either from silently overlapping the arc.
    float reference=TrioGeometry.arcRowDotR(0,stroke,width);
    float room=Math.max(reference,TrioGeometry.maxBeadR());
    ok("stroke "+stroke+" at "+pct+"% is capped by a real limit, not the slider",
       r<=Math.max(reference,room)+0.01f);
   }
   // Scaling up can never make the bead smaller than the reference: the cap only binds
   // from above, so a large setting is monotonic up to the ceiling.
   float normal=TrioGeometry.arcRowDotR(0,stroke,width,1f);
   float big=TrioGeometry.arcRowDotR(0,stroke,width,Prefs.MAX_DOT_SCALE/100f);
   ok("a larger setting never draws a smaller bead at stroke "+stroke,big>=normal-1e-6f);
  }
  // 4. The setting round-trips, and a stored value outside the span is clamped rather
  // than trusted - the hooked side reads the broadcast extras as well as the file.
  {
   TrioSettings s=TrioSettings.defaults();
   ok("a fresh install is at the reference size",s.dotScale==Prefs.DEF_DOT_SCALE);
   TrioSettings tweaked=s.copy();tweaked.dotScale=Prefs.MAX_DOT_SCALE;
   ok("the dot size survives copy()",tweaked.copy().dotScale==Prefs.MAX_DOT_SCALE);
   ok("the dot size survives the bundle",TrioSettings.fromBundle(
        tweaked.toBundle()).dotScale==Prefs.MAX_DOT_SCALE);
   ok("the dot size participates in equality",!s.equals(tweaked));
   ok("the remote key update carries it",s.applyKeyFrom(tweaked,Prefs.KEY_DOT_SCALE)
        && s.dotScale==Prefs.MAX_DOT_SCALE);
   TrioSettings wild=s.copy();wild.dotScale=9999;
   ok("the multiplier clamps a wild value",
      TrioAppearance.of(wild).dotScaleFactor()==Prefs.MAX_DOT_SCALE/100f);
  }
 }
 /**
  * The out-of-ring order: a stored arrangement that decides placement only.
  *
  * <p>Three claims. The codec survives junk and always yields three slots, because it is
  * read from a preference file and from broadcast extras. The arrangement actually moves the
  * views through {@link OutRingLayout}, since an order that reaches the settings but not the
  * layout would look saved and do nothing. And the sacrifice order stays a separate
  * judgement: reordering must not change which piece survives an overflow, which is the whole
  * reason the two were split.
  */
 static void outorder() {
  // 1. The codec: order preserved, junk repaired, always three entries.
  ok("the default order is the shipped arrangement",
     Arrays.equals(Prefs.decodeOutOrder(null),new int[]{
        Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_TYPE}));
  ok("a stored order round-trips",
     Arrays.equals(Prefs.decodeOutOrder("3,1,2"),new int[]{
        Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS}));
  ok("the codec re-encodes to the same string",
     "3,1,2".equals(Prefs.encodeOutOrder(Prefs.decodeOutOrder("3,1,2"))));
  // A partial value keeps what the user did express and appends the rest in default order,
  // rather than resetting the whole arrangement over one damaged id.
  ok("a partial order keeps the entry that survived",
     Arrays.equals(Prefs.decodeOutOrder("3"),new int[]{
        Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS}));
  ok("an unknown id is dropped",Prefs.decodeOutOrder("9,1,2,3").length==Prefs.OUT_SLOT_COUNT);
  ok("a duplicate is collapsed",
     Arrays.equals(Prefs.decodeOutOrder("1,1,2,3"),new int[]{
        Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_TYPE}));
  ok("an empty string is the default order",
     Arrays.equals(Prefs.decodeOutOrder(""),Prefs.OUT_DEFAULT_ORDER));
  ok("every decoded order names each slot exactly once",distinctSlots(Prefs.decodeOutOrder("x")));
  // 2. The transport: the arrangement survives copy, bundle and the remote key update.
  {
   TrioSettings s=TrioSettings.defaults();
   ok("a fresh install has the shipped arrangement",
      Arrays.equals(s.outOrder,Prefs.OUT_DEFAULT_ORDER));
   s.outOrder=new int[]{Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_READOUTS,Prefs.OUT_SLOT_SIGNAL};
   ok("the order survives copy()",Arrays.equals(s.copy().outOrder,s.outOrder));
   ok("copy() does not share the array",s.copy().outOrder!=s.outOrder);
   ok("the order survives the bundle",
      Arrays.equals(TrioSettings.fromBundle(s.toBundle()).outOrder,s.outOrder));
   TrioSettings other=TrioSettings.defaults();
   ok("the remote key update carries it",other.applyKeyFrom(s,Prefs.KEY_OUT_ORDER)
      && Arrays.equals(other.outOrder,s.outOrder));
   ok("the order participates in equality",!TrioSettings.defaults().equals(s));
   ok("the appearance exposes the same arrangement",
      Arrays.equals(TrioAppearance.of(s).outOrder,s.outOrder));
  }
  // 3. The layout: the arrangement decides placement, and only placement.
  //
  // The coordinate convention, which the editor has to mirror: `near` is measured from the
  // row's END, so the slot the solver counts FIRST sits closest to the battery and therefore
  // furthest to the RIGHT in LTR. Index 0 is "next to the battery", not "leftmost" - that is
  // the mapping the settings row has to reverse when it draws its tiles.
  {
   int[] widths={20,40,30}, heights={20,20,20}, gaps={3,5,5};
   OutRingLayout.Plan shipped=OutRingLayout.solve(300,0,0,0,40,widths,heights,gaps,7);
   ok("index 0 is placed at all",shipped.widths[0]>0);
   ok("index 0 sits nearest the row's end, i.e. nearest the battery",
      shipped.near[0]>shipped.near[1] && shipped.near[1]>shipped.near[2]);
   // Reversed arrangement: the same widths, permuted. The same index now carries a different
   // piece, so its coordinate moves - that is what a reorder has to look like.
   int[] revWidths={widths[2],widths[1],widths[0]};
   int[] revGaps={gaps[2],gaps[1],gaps[0]};
   OutRingLayout.Plan reversed=OutRingLayout.solve(300,0,0,0,40,revWidths,heights,revGaps,7);
   ok("a permuted arrangement places the same pieces",
      Arrays.equals(sorted(shipped.widths),sorted(reversed.widths)));
   ok("the arrangement moves the geometry",
      Math.abs(shipped.near[2]-reversed.near[2])>0.5f);
   // And the screen order the editor renders is the reverse of this array: the LAST stored
   // entry is the leftmost tile. Stated as arithmetic so the two cannot drift apart.
   int[] stored={Prefs.OUT_SLOT_SIGNAL,Prefs.OUT_SLOT_TYPE,Prefs.OUT_SLOT_READOUTS};
   int[] onScreen={stored[2],stored[1],stored[0]};
   ok("the leftmost tile is the piece furthest from the battery",
      onScreen[0]==Prefs.OUT_SLOT_READOUTS && stored[2]==Prefs.OUT_SLOT_READOUTS);
   ok("the rightmost tile is the piece next to the battery",
      onScreen[onScreen.length-1]==stored[0]);
  }
  // 4. Priority is independent: overflow drops the same piece whatever the arrangement.
  {
   int[] widths={40,40,40}, heights={20,20,20}, gaps={5,5,5};
   // No drop order: the old positional rule, which sacrifices from the end.
   OutRingLayout.Plan positional=OutRingLayout.solve(30,0,0,0,40,widths,heights,gaps,0);
   ok("positional rule drops from the end",positional.dropped[2]);
   // An explicit priority that names slot 0 first must sacrifice slot 0 instead, whatever
   // position it occupies - that is the separation the setting exists for.
   OutRingLayout.Plan priority=OutRingLayout.solve(30,0,0,0,40,widths,heights,gaps,0,
      new int[]{0,1,2});
   ok("an explicit priority wins over the position",priority.dropped[0]);
   ok("and it drops exactly one before giving up",
      priority.dropped[0] && !priority.dropped[1] && !priority.dropped[2]);
   // A partial order still terminates: everything it names is gone, so the fallback takes
   // over rather than looping forever.
   OutRingLayout.Plan partial=OutRingLayout.solve(1,0,0,0,40,widths,heights,gaps,0,
      new int[]{0});
   ok("a partial drop order still terminates",partial.dropped[0]);
   ok("the default order and the priority agree on the shipped arrangement",
      Arrays.equals(Prefs.OUT_DEFAULT_ORDER,Prefs.OUT_PRIORITY));
  }
 }
 /** True when an order names each slot exactly once. */
 static boolean distinctSlots(int[] order) {
  if(order.length!=Prefs.OUT_SLOT_COUNT) return false;
  boolean[] seen=new boolean[Prefs.OUT_SLOT_COUNT+1];
  for(int id:order) {
   if(id<Prefs.OUT_SLOT_SIGNAL||id>Prefs.OUT_SLOT_COUNT||seen[id]) return false;
   seen[id]=true;
  }
  return true;
 }
 /** A sorted copy, so two arrangements can be compared as multisets. */
 static int[] sorted(int[] values) {
  int[] copy=values.clone();
  Arrays.sort(copy);
  return copy;
 }
 /**
  * The capsule's two dot rows are editable placements, and an install that never edited one
  * still draws exactly what it used to.
  *
  * <p>The defect this pins: the openings drew dots from the mobile switch alone, so the user
  * saw signal dots in the top and bottom bands and no setting could move or remove them - the
  * bands opened a picker that offered readings only, and the rows were not in any list. The
  * fix makes them band occupants like everything else; the compatibility claim is that the
  * legacy no-placement path reproduces the old look, which is why the two rows are asserted
  * on instead of merely "dots are still drawn somewhere".
  */
 static void capsule() {
  // 1. A capsule with nothing placed keeps the shipped look: both rows, from the switch.
  {
   TrioSettings legacy=TrioSettings.defaults();
   legacy.trioStyle=Prefs.STYLE_CAPSULE;
   legacy.ringZones=Prefs.decodeLayout(Prefs.EMPTY_LAYOUT);
   TrioAppearance a=TrioAppearance.of(legacy);
   TrioAppearance.Capsule cap=a.capsule(79,false,-1,"5GA",2);
   ok("an untouched capsule still draws dots",cap.dots);
   ok("an untouched capsule draws the top row",cap.dotsIn(TrioZones.Zone.TOP));
   ok("an untouched capsule draws the bottom row",cap.dotsIn(TrioZones.Zone.BOTTOM));
   // Out of ring releases the rows exactly as before.
   TrioSettings outside=legacy.copy();
   outside.signalMode=Prefs.SIGNAL_OUT_RING;
   ok("out of ring still releases both rows",
      !TrioAppearance.of(outside).capsule(79,false,-1,"5GA",2).dots);
  }
  // 2. The rows are placements: they can be removed, and each opening is independent.
  {
   TrioSettings cleared=TrioSettings.defaults();
   cleared.trioStyle=Prefs.STYLE_CAPSULE;
   // A reading in the interior and nothing in the openings: the user removed both rows.
   cleared.ringZones=Prefs.decodeLayout("|3|");
   TrioAppearance.Capsule cap=TrioAppearance.of(cleared).capsule(79,false,-1,"5GA",2);
   ok("a capsule with an interior reading draws no dots",!cap.dots);
   ok("and neither opening holds a row",
      !cap.dotsIn(TrioZones.Zone.TOP) && !cap.dotsIn(TrioZones.Zone.BOTTOM));
   ok("the interior reading survives",cap.parts[TrioZones.Zone.CENTRE.ordinal()]
      !=Prefs.PART_NONE);

   // SIM 1 in the top opening only: the two bands are independent.
   TrioSettings topOnly=TrioSettings.defaults();
   topOnly.trioStyle=Prefs.STYLE_CAPSULE;
   topOnly.showSim1=true;
   topOnly.ringZones=Prefs.decodeLayout("7|3|");
   TrioAppearance.Capsule top=TrioAppearance.of(topOnly).capsule(79,false,-1,"5GA",2);
   ok("a top-only placement draws the top row",top.dotsIn(TrioZones.Zone.TOP));
   ok("and leaves the bottom opening empty",!top.dotsIn(TrioZones.Zone.BOTTOM));
   ok("one row still counts as dots for the outline",top.dots);

   // The bottom opening is a placement target in its own right.
   TrioSettings bottomOnly=TrioSettings.defaults();
   bottomOnly.trioStyle=Prefs.STYLE_CAPSULE;
   bottomOnly.showSim2=true;
   bottomOnly.ringZones=Prefs.decodeLayout("|3|8");
   TrioAppearance.Capsule bot=TrioAppearance.of(bottomOnly).capsule(79,false,-1,"5GA",2);
   ok("a bottom-only placement draws the bottom row",bot.dotsIn(TrioZones.Zone.BOTTOM));
   ok("and leaves the top opening empty",!bot.dotsIn(TrioZones.Zone.TOP));
  }
  // 3. The editor can name what it sees: the occupant of a band holding dots is a piece the
  // picker offers, which is what makes the row editable rather than merely visible.
  {
   TrioSettings topOnly=TrioSettings.defaults();
   topOnly.trioStyle=Prefs.STYLE_CAPSULE;
   topOnly.showSim1=true;
   topOnly.ringZones=Prefs.decodeLayout("7|3|");
   TrioAppearance a=TrioAppearance.of(topOnly);
   ok("the top band reports an occupant",
      TrioZones.occupant(TrioZones.Zone.TOP,a,79,false,-1,"5GA",2)!=null);
   // And that band is offered the rows, so the user can put one there or take it away.
   java.util.List<TrioZones.Part> choices=
      TrioZones.placementChoices(TrioZones.Zone.TOP,topOnly,2);
   ok("the top band is offered the per-card rows",
      choices.contains(TrioZones.Part.SIM_1) || choices.contains(TrioZones.Part.SIM_2));
   // Both openings are bands the solver writes to.
   ok("a row can be placed in the top opening",
      TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.SIM_2,topOnly,2)!=null);
   ok("a row can be placed in the bottom opening",
      TrioZones.assign(TrioZones.Zone.BOTTOM,false,TrioZones.Part.SIM_1,topOnly,2)!=null);
   // Clearing a band that holds a row leaves it empty rather than falling back to the switch.
   TrioSettings emptied=TrioZones.withPiece(topOnly,TrioZones.Zone.TOP.ordinal(),null);
   ok("clearing the top opening removes its row",
      !TrioAppearance.of(emptied).capsule(79,false,-1,"5GA",2).dotsIn(TrioZones.Zone.TOP));
  }
  // 4. The capsule is slot-driven for the rows but still refuses what it cannot draw: the
  // clock is fixed in its own column and is not a placement.
  {
   TrioSettings s=TrioSettings.defaults();
   s.trioStyle=Prefs.STYLE_CAPSULE;
   ok("the capsule still refuses to place the clock",
      TrioZones.assign(TrioZones.Zone.TOP,false,TrioZones.Part.CLOCK,s,2)==null);
   ok("the capsule answers hasSlots",TrioZones.hasSlots(s));
   TrioSettings rect=s.copy();rect.trioStyle=Prefs.STYLE_RECT;
   ok("the rectangle has no bands",!TrioZones.hasSlots(rect));
  }
  // 5. A reading in an opening straddles the outline, in the gap the dot row opened, and its
  // whole ink stays inside the box the glyph is fitted to.
  //
  // This is the "space design is wrong" defect, and it had two halves. The first was that the
  // cell sat in a lane INSIDE the capsule, stacked on the clock and the percentage the body
  // already draws there - so an opening was not a place of its own. The second was that even
  // there it could not be drawn large: the lane was measured against the strip the ink box
  // left beyond the stroke, which is about one stroke wide.
  //
  // The fix is that the opening is a real row. The reading is centred on the path line, and the
  // host grows by CAP_BAND_H above and below so the half of the line outside the capsule has
  // somewhere to land. The capsule itself does not change size: the fit is width-driven and the
  // width is untouched (pinned below), so every pixel of it is where it was.
  {
   for(int ringStroke=Prefs.MIN_RING_STROKE;ringStroke<=Prefs.MAX_RING_STROKE;ringStroke++) {
    float stroke=ringStroke*TrioGeometry.CAP_STROKE_RATIO;
    float room=TrioGeometry.capOpeningTextRoomH(stroke);
    float topCy=TrioGeometry.capOpeningCy(true,stroke);
    float bottomCy=TrioGeometry.capOpeningCy(false,stroke);
    // Centred on the path line: the label reads as the row that interrupted the outline.
    ok("stroke "+ringStroke+": the top cell is centred on the outline",
       Math.abs(topCy-TrioGeometry.CAP_TOP)<0.001f);
    ok("stroke "+ringStroke+": the bottom cell is centred on the outline",
       Math.abs(bottomCy-TrioGeometry.CAP_BOTTOM)<0.001f);
    // Its ink fits the banded box, so the host does not clip it.
    ok("stroke "+ringStroke+": the top cell stays inside the banded box",
       topCy-room*0.5f>=TrioGeometry.CAP_INK_TOP-0.001f);
    ok("stroke "+ringStroke+": the bottom cell stays inside the banded box",
       bottomCy+room*0.5f<=TrioGeometry.CAP_INK_BOTTOM+0.001f);
    // The CAPSULE's own box is unchanged. This is the compatibility pin: it is what keeps the
    // width asked of the status bar - and so the capsule's size in pixels - exactly as shipped,
    // because the fit is width-driven and CAP_INK_W is untouched by the bands.
    ok("stroke "+ringStroke+": the capsule box still ends at the outline plus its stroke",
       Math.abs((TrioGeometry.CAP_INK_Y+TrioGeometry.CAP_INK_H)
          -(TrioGeometry.CAP_BOTTOM+Prefs.MAX_RING_STROKE
            *TrioGeometry.CAP_STROKE_RATIO*0.5f))<0.001f);
    // The bands are exactly what was added, on both sides.
    ok("stroke "+ringStroke+": the banded box is the capsule box plus both bands",
       Math.abs(TrioGeometry.CAP_INK_TOP-(TrioGeometry.CAP_INK_Y-TrioGeometry.CAP_BAND_H))<0.001f
       && Math.abs(TrioGeometry.CAP_INK_BOTTOM
            -(TrioGeometry.CAP_INK_Y+TrioGeometry.CAP_INK_H+TrioGeometry.CAP_BAND_H))<0.001f);
   }
   // The openings no longer fight the body for space: a reading there is half outside the
   // capsule, so the two cannot overlap the clock in the middle.
   float stroke=Prefs.DEF_RING_STROKE*TrioGeometry.CAP_STROKE_RATIO;
   float room=TrioGeometry.capOpeningTextRoomH(stroke);
   ok("the opening readings sit clear of the body",
      TrioGeometry.capOpeningCy(true,stroke)+room*0.5f
      <= TrioGeometry.capInteriorTop(stroke)+0.5f);
   // And the host really does grow, in HEIGHT only - the width the status bar is asked for is
   // unchanged, which is the whole reason the capsule does not shrink.
   int bare=60;
   ok("the host grows taller", TrioGeometry.capHostHeightPx(bare)>bare);
   ok("the host width is untouched by the bands",
      TrioGeometry.capHostWidthPx(bare)==Math.round(
         bare*(TrioGeometry.CAP_INK_W/TrioGeometry.CAP_INK_H)));
   ok("and the grown height keeps the same aspect the width was asked for",
      Math.abs((TrioGeometry.capHostHeightPx(bare)-bare)
         -2f*TrioGeometry.CAP_BAND_H*(bare/TrioGeometry.CAP_INK_H))<=1f);
  }
 }
 /**
  * The ring and the capsule keep their own components and layout.
  *
  * <p>Before the split both arrangements read and wrote the same three slots and the same six
  * switches, so setting up one silently reconfigured the other: placing the percentage in the
  * ring's middle and then switching to the capsule emptied one of the two. The fix moves the
  * component keys to a per-arrangement name (`show_wifi` for the ring, `show_wifi@capsule` for
  * the capsule) while leaving every size, weight and colour shared.
  *
  * <p>Three claims: the keys map as intended, an existing install inherits its ring values into
  * the capsule rather than getting defaults, and an edit to either arrangement leaves the other
  * untouched in BOTH directions - which is the property the user actually notices.
  */
 static void isolation() {
  // 1. The key mapping. The ring keeps the historical unsuffixed name, so no stored value
  //    has to move; only the capsule gets a new name.
  ok("the ring keeps the historical key",
     Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_RING).equals(Prefs.KEY_SHOW_WIFI));
  ok("the capsule gets its own key",
     Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE)
        .equals(Prefs.KEY_SHOW_WIFI+Prefs.STYLE_SUFFIX));
  ok("the layout is per arrangement",
     Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE)
        .equals(Prefs.KEY_RING_LAYOUT+Prefs.STYLE_SUFFIX));
  // Appearance is NOT per arrangement: a user who chose 40pt digits wants them in both.
  for(String key:new String[]{Prefs.KEY_RING_STROKE,Prefs.KEY_VALUE_SIZE,
      Prefs.KEY_TYPE_WEIGHT,Prefs.KEY_DOT_SCALE,Prefs.KEY_GLYPH_SCALE}) {
   ok("appearance stays shared: "+key,
      Prefs.styledKey(key,Prefs.STYLE_CAPSULE).equals(key));
  }
  ok("the base of a suffixed key is recovered",
     Prefs.styledBase(Prefs.KEY_RING_LAYOUT+Prefs.STYLE_SUFFIX).equals(Prefs.KEY_RING_LAYOUT));

  // 2. Migration: an install that only ever had ring values hands them to the capsule.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.KEY_SHOW_WIFI,false);
   prefs.m.put(Prefs.KEY_SHOW_VALUE,false);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule inherits the pre-split switch",!capsule.showWifi && !capsule.showValue);
   ok("the capsule inherits the pre-split layout",
      java.util.Arrays.equals(capsule.ringZones[0],new int[]{Prefs.PART_WIFI}));
  }

  // 3. Isolation, capsule edit first: the ring must be untouched.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.KEY_SHOW_WIFI,true);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   // The user switches to the capsule and edits it.
   prefs.m.put(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE),false);
   prefs.m.put(Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE),"||1");
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule reads its own switch",!capsule.showWifi);
   ok("the capsule reads its own layout",
      java.util.Arrays.equals(capsule.ringZones[2],new int[]{Prefs.PART_WIFI}));
   TrioSettings ring=TrioSettings.from(prefs,3f,Prefs.STYLE_RING);
   ok("the ring kept its switch after a capsule edit",ring.showWifi);
   ok("the ring kept its layout after a capsule edit",
      java.util.Arrays.equals(ring.ringZones[0],new int[]{Prefs.PART_WIFI}));
   ok("and the capsule layout did not leak upward",ring.ringZones[2].length==0);
  }

  // 4. And the other way: a ring edit must leave the capsule alone.
  {
   Map prefs=new Map();
   prefs.m.put(Prefs.styledKey(Prefs.KEY_SHOW_VALUE,Prefs.STYLE_CAPSULE),true);
   prefs.m.put(Prefs.styledKey(Prefs.KEY_RING_LAYOUT,Prefs.STYLE_CAPSULE),"||1");
   // The user goes back to the ring and changes it.
   prefs.m.put(Prefs.KEY_SHOW_VALUE,false);
   prefs.m.put(Prefs.KEY_RING_LAYOUT,"1|3|");
   TrioSettings ring=TrioSettings.from(prefs,3f,Prefs.STYLE_RING);
   ok("the ring edit applied to the ring",!ring.showValue);
   TrioSettings capsule=TrioSettings.from(prefs,3f,Prefs.STYLE_CAPSULE);
   ok("the capsule kept its own value switch",capsule.showValue);
   ok("the capsule kept its own layout",
      java.util.Arrays.equals(capsule.ringZones[2],new int[]{Prefs.PART_WIFI}));
   ok("and did not inherit the ring's new placement",
      !java.util.Arrays.equals(capsule.ringZones[0],new int[]{Prefs.PART_WIFI}));
  }

  // 5. The remote-preference and broadcast paths must speak the suffixed keys too, or a
  //    capsule edit would reach storage and never reach the hooked process.
  {
   TrioSettings snap=TrioSettings.defaults();
   snap.trioStyle=Prefs.STYLE_CAPSULE;
   snap.showWifi=false;
   TrioSettings target=TrioSettings.defaults();
   ok("a suffixed remote key writes its field",
      target.applyKeyFrom(snap,Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE))
      && !target.showWifi);
   android.os.Bundle bundle=snap.toBundle();
   ok("the capsule bundle carries its own key",
      bundle.containsKey(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE)));
   ok("and the value it carries",
      !bundle.getBoolean(Prefs.styledKey(Prefs.KEY_SHOW_WIFI,Prefs.STYLE_CAPSULE),true));
   TrioSettings ringSnap=TrioSettings.defaults();
   ringSnap.trioStyle=Prefs.STYLE_RING;
   ok("the ring's bundle keeps the unsuffixed key",
      ringSnap.toBundle().containsKey(Prefs.KEY_SHOW_WIFI));
  }
 }
 /** Minimal in-memory prefs, so these cases exercise the real readers. */
 static final class Map implements android.content.SharedPreferences {
  final java.util.Map<String,Object> m=new java.util.HashMap<>();
  public boolean getBoolean(String k,boolean d){Object v=m.get(k);return v==null?d:(Boolean)v;}
  public int getInt(String k,int d){Object v=m.get(k);return v==null?d:(Integer)v;}
  public String getString(String k,String d){Object v=m.get(k);return v==null?d:(String)v;}
  public boolean contains(String k){return m.containsKey(k);}
  public android.content.SharedPreferences.Editor edit(){
   return new android.content.SharedPreferences.Editor(){
    final java.util.Map<String,Object> pending=new java.util.HashMap<>();
    public android.content.SharedPreferences.Editor putBoolean(String k,boolean v){
     pending.put(k,v);return this;}
    public android.content.SharedPreferences.Editor putInt(String k,int v){
     pending.put(k,v);return this;}
    public android.content.SharedPreferences.Editor putString(String k,String v){
     pending.put(k,v);return this;}
    public boolean commit(){m.putAll(pending);pending.clear();return true;}
   };
  }
 }
 public static void main(String[] args)throws Throwable {
  switch(args[0]){case "signal":signal();break;case "tint":tint();break;case "assignment":assignment();break;case "wifi":wifi();break;case "clear":clear();break;case "clock":clock();break;case "centre":centre();break;case "dots":dots();break;case "outorder":outorder();break;case "capsule":capsule();break;case "isolation":isolation();break;default:throw new AssertionError(args[0]);}
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
#
# The long press now has two branches, because the battery ring became its own
# edit target: pressing the arc opens the ring's settings, pressing anything else
# opens the component's. What the original pin was protecting is unchanged and is
# still asserted here - the branch must read the CURRENT lambda, never one captured
# at composition, or a long press would edit whatever was on screen several edits
# ago. So the check is that the callback is read through rememberUpdatedState and
# that every branch calls it through .value, rather than that the block has the one
# exact shape it had before the ring became clickable.
$glyph = [regex]::Match($drawing, '(?s)private fun DrawingGlyph\(.*?\n\}').Value
if (!$glyph -or $glyph -notmatch 'currentEdit = rememberUpdatedState\(onEditZone\)' -or
    $glyph -notmatch '(?s)onLongPress = \{ offset ->.*?currentEdit\.value\(it\)') {
    throw 'A glyph long press must use the current edit callback'
}
if ($glyph -match 'onLongPress = \{ offset -> zoneAt\(offset\)') {
    throw 'A glyph long press must not skip the battery-ring branch'
}
if ($drawing -notmatch '(?s)onEditZone = \{ zone ->.*?TrioZones\.occupant\(.*?if \(part == null\) \{\s*picking = zone\s*\} else \{\s*editing = TrioSettingsPart\.of\(part\)') {
    throw 'Long press must edit the visible component or pick one for an empty zone'
}
# The layout is a priority list now, so an edit is `withPiece` rather than a solved
# one-piece assignment. What the contract pins is unchanged: the tap must APPLY the new
# layout through the single-write path, must remember what was chosen, and must compare
# that choice against the frame actually being drawn.
if ($drawing -notmatch 'val next = TrioZones\.withPiece\(settings, pickZone\.ordinal, part\)' -or
    $drawing -notmatch 'applyZoneAssignment\(next, r, settings\)' -or
    $drawing -notmatch 'lastSelection = part\?\.let \{ ZoneSelection\(pickZone, it\) \}' -or
    $drawing -notmatch 'PlacementFeedback\(selection, appearance, scene, sims\)' -or
    $drawing -notmatch 'if \(actual == selection\.part\) return') {
    throw 'Selection feedback must compare the applied choice with this preview frame'
}
if ($drawing -notmatch 'TrioZones\.withPiece\(settings, zone\.ordinal, null\)' -and
    $drawing -notmatch 'TrioZones\.withoutAt\(settings, zone\.ordinal, index\)') {
    throw 'Removing a piece must go through the layout rather than clearing a switch'
}
if ($drawing -notmatch 'TrioZones\.moveWithin\(settings, zone\.ordinal, index, delta\)') {
    throw 'Reordering must go through the priority list'
}
foreach ($name in @('charging','bolt','wifi','other')) {
    if ($drawing -notmatch "R\.string\.drawing_feedback_$name") { throw "Missing preview feedback: $name" }
}
Write-Output 'PASS: long-press and preview-feedback source contracts (Android gestures not executed)'

